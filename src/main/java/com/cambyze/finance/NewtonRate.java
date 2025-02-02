package com.cambyze.finance;

import static java.util.Map.Entry.comparingByKey;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class NewtonRate {
	private static final int maxNumberIterations = 500;
	private static final int internalComputationsScale = 20;
	private static final BigDecimal maximumRange = BigDecimal.TEN.pow(100);
	private static final BigDecimal epsilon = BigDecimal.valueOf(1E-10);

	public static Optional<BigDecimal> findZero(
			Function<BigDecimal, BigDecimal> function,
			Function<BigDecimal, BigDecimal> derivative) {
		BigDecimal x = BigDecimal.ZERO;
		int iterationsCounter = 0;
		do {
			BigDecimal derivativeAtX = derivative.apply(x);
			if (derivativeAtX.compareTo(BigDecimal.ZERO) == 0) {
				return Optional.empty();
			}
			if (x.compareTo(BigDecimal.ZERO) < 0) {
				x = BigDecimal.ZERO;
			}
			if (iterationsCounter++ > maxNumberIterations) {
				return Optional.empty();
			}
			if (x.compareTo(maximumRange) > 0) {
				return Optional.empty();
			}
			x = x.subtract(function.apply(x).divide(derivativeAtX,
					internalComputationsScale, BigDecimal.ROUND_HALF_UP));
		} while (errorIsLargerThanExpected(function, x, epsilon));
		return Optional.of(x);
	}

	private static boolean errorIsLargerThanExpected(
			Function<BigDecimal, BigDecimal> function, BigDecimal x,
			BigDecimal epsilon) {
		return function.apply(x).abs().compareTo(epsilon) >= 0;
	}

	public static BigDecimal calculate(
			LinkedHashMap<LocalDate, BigDecimal> cashFlow) {
		if (cashFlow == null) {
			throw new NullPointerException("CashFlow should be defined");
		}

		if (cashFlow.isEmpty()) {
			return BigDecimal.ZERO;
		}

		return NewtonRate
				.findZero(x -> cashFlowSum(cashFlow, x),
						x -> cashFlowSumDerivative(cashFlow, x))
				.orElseThrow(() -> new UnsupportedOperationException(
						"Can't find APR for cash flow " + cashFlow.toString()))
				.multiply(BigDecimal.valueOf(100));
	}

	private static BigDecimal cashFlowSum(
			LinkedHashMap<LocalDate, BigDecimal> cashFlow, BigDecimal x) {
		LocalDate startDate = cashFlow.entrySet().stream()
				.min(Comparator.comparing(Map.Entry::getKey)).get().getKey();

		return cashFlow.entrySet().stream()
				.map(entry -> discountPayment(startDate, entry, x))
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private static BigDecimal discountPayment(LocalDate startDate,
			Map.Entry<LocalDate, BigDecimal> entry, BigDecimal x) {
		return entry.getValue().multiply(BigDecimal.valueOf(Math
				.pow(1.0 + x.doubleValue(), -days(startDate, entry) / 365.0)));
	}

	private static BigDecimal cashFlowSumDerivative(
			LinkedHashMap<LocalDate, BigDecimal> cashFlow, BigDecimal x) {
		LocalDate startDate = cashFlow.entrySet().stream()
				.min(comparingByKey(LocalDate::compareTo)).get().getKey();
		return cashFlow.entrySet().stream().map(entry -> entry.getValue()
				.multiply(BigDecimal.valueOf(Math.pow(1 + x.doubleValue(),
						-days(startDate, entry) / 365.0 - 1)))
				.multiply(BigDecimal.valueOf(-days(startDate, entry)).divide(
						BigDecimal.valueOf(365.0), internalComputationsScale,
						BigDecimal.ROUND_HALF_UP)))
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private static long days(LocalDate startDate,
			Map.Entry<LocalDate, BigDecimal> entry) {
		return ChronoUnit.DAYS.between(startDate, entry.getKey());
	}
}
