package com.cambyze.finance;

import static java.util.Map.Entry.comparingByKey;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * YTM rate calculation by using
 * <a href="https://en.wikipedia.org/wiki/Newton%27s_method">Newton's method</a>
 * </p>
 * 
 * @author Thierry NESTELHUT
 * 
 */
public class FinancialCalculation {

	private static final Logger LOGGER = LoggerFactory
			.getLogger(FinancialCalculation.class);

	private static final int MAX_NUMBER_OF_ITERATIONS = 5000;
	private static final int SCALE = 20;
	private static final BigDecimal MAX_RANGE = BigDecimal.TEN.pow(200);
	private static final BigDecimal EPSILON = BigDecimal.valueOf(1E-6);

	private static final Locale LOCALE = new Locale("en", "US");
	private static final NumberFormat CURRENCY_FORMATTER = NumberFormat
			.getCurrencyInstance(LOCALE);

	/**
	 * <p>
	 * <a href="https://en.wikipedia.org/wiki/Newton%27s_method">Newton's
	 * method</a> to try to calculate approximatively zero
	 * </p>
	 */
	private static Optional<BigDecimal> findZero(
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
			if (iterationsCounter++ > MAX_NUMBER_OF_ITERATIONS) {
				return Optional.empty();
			}
			if (x.compareTo(MAX_RANGE) > 0) {
				return Optional.empty();
			}
			x = x.subtract(function.apply(x).divide(derivativeAtX, SCALE,
					RoundingMode.HALF_UP));
		} while (errorIsLargerThanExpected(function, x, EPSILON));
		return Optional.of(x);
	}

	private static boolean errorIsLargerThanExpected(
			Function<BigDecimal, BigDecimal> function, BigDecimal rate,
			BigDecimal epsilon) {
		return function.apply(rate).abs().compareTo(epsilon) >= 0;
	}

	/**
	 * <p>
	 * <b>This function allows to calculate YTM rates as APR, IRR based on the
	 * cashflow you provide</b>
	 * </p>
	 * <p>
	 * Rate calculation by using the
	 * <a href="https://en.wikipedia.org/wiki/Newton%27s_method">Newton's
	 * method</a>
	 * </p>
	 * <p>
	 * <p>
	 * <b>In french</b>: Fonction pour calculer des <b>"taux actuariel" (TAEG,
	 * TRI, ...)</b> à partir d'un flux de montants
	 * </p>
	 * <p>
	 * The cashflow is composed by negative amounts for investments, positive
	 * amount for received/expected payments
	 * </p>
	 * <p>
	 * NB: This calculation is based on <b>years of 365 days</b> independently
	 * of the real number of days of each year
	 * </p>
	 * 
	 * @param cashFlow
	 *            cashflow of date/amount as a Hash table and linked list
	 * @return the calculated YTM rate
	 * 
	 */
	public static BigDecimal calculateYTM(
			LinkedHashMap<LocalDate, BigDecimal> cashFlow) {

		if (cashFlow == null || cashFlow.isEmpty()) {
			return BigDecimal.ZERO;
		}

		BigDecimal rate = FinancialCalculation
				.findZero(x -> cashFlowSum(cashFlow, x),
						x -> cashFlowSumDerivative(cashFlow, x))
				.orElseThrow(() -> new UnsupportedOperationException(
						"Can't find YTM rate for cash flow "
								+ cashFlow.toString()))
				.multiply(BigDecimal.valueOf(100));
		MathContext m = new MathContext(6); // 6 precision
		return rate.round(m);
	}

	private static BigDecimal cashFlowSum(
			LinkedHashMap<LocalDate, BigDecimal> cashFlow, BigDecimal rate) {

		// Determine the start date of the cashflow
		LocalDate startDate = cashFlow.entrySet().stream()
				.min(Comparator.comparing(Map.Entry::getKey)).get().getKey();

		return cashFlow.entrySet().stream()
				.map(entry -> discountPayment(startDate, entry, rate))
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	/**
	 * Calculation of discount payment for a date and a YTM rate
	 * <p>
	 * <b>In french: </b>Actualisation d'un montant à une date et un taux
	 * actuariel donné
	 * </p>
	 * <p>
	 * NB: This calculation is based on <b>years of 365 days</b> independently
	 * of the real number of days of each year
	 * </p>
	 * 
	 * @param startDate
	 *            date for which the payment is discounted
	 * @param payment
	 *            (date/amount)
	 * @param rate
	 *            discount rate
	 * @return
	 */
	public static BigDecimal discountPayment(LocalDate startDate,
			Map.Entry<LocalDate, BigDecimal> payment, BigDecimal rate) {
		LOGGER.debug(
				"Parameters: " + startDate + " / " + payment + " / " + rate);
		BigDecimal discountAmount = payment.getValue()
				.multiply(BigDecimal.valueOf(Math.pow(1.0 + rate.doubleValue(),
						-daysBetweenPayments(startDate, payment) / 365.0)));
		LOGGER.debug(
				"discount payment :" + CURRENCY_FORMATTER.format(discountAmount)
						+ " for " + payment.getKey() + " => "
						+ CURRENCY_FORMATTER.format(payment.getValue()));

		return discountAmount;
	}

	private static BigDecimal cashFlowSumDerivative(
			LinkedHashMap<LocalDate, BigDecimal> cashFlow, BigDecimal rate) {
		// Determine the start date of the cashflow
		LocalDate startDate = cashFlow.entrySet().stream()
				.min(comparingByKey(LocalDate::compareTo)).get().getKey();

		return cashFlow.entrySet().stream().map(entry -> entry.getValue()
				.multiply(BigDecimal.valueOf(Math.pow(1 + rate.doubleValue(),
						-daysBetweenPayments(startDate, entry) / 365.0 - 1)))
				.multiply(BigDecimal
						.valueOf(-daysBetweenPayments(startDate, entry))
						.divide(BigDecimal.valueOf(365.0), SCALE,
								RoundingMode.HALF_UP)))
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	/**
	 * Calculate the number of days between dates
	 * 
	 * @param startDate
	 *            start date
	 * @param payment
	 *            (date/amount)
	 * @return number of days between dates
	 */
	public static long daysBetweenPayments(LocalDate startDate,
			Map.Entry<LocalDate, BigDecimal> payment) {
		return ChronoUnit.DAYS.between(startDate, payment.getKey());
	}
}
