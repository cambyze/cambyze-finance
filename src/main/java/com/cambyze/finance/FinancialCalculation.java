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
 * Library for financial calculation as YTM (APR,IRR,...) calculation, discount
 * amount,...
 * </p>
 * 
 * @author Thierry NESTELHUT
 * @author CAMBYZE
 * @see <a href="https://cambyze.com">Cambyze</a>
 * 
 */
public class FinancialCalculation {

	private static final Logger LOGGER = LoggerFactory
			.getLogger(FinancialCalculation.class);

	private static final int MAX_NUMBER_OF_ITERATIONS = 5000;
	private static final int SCALE = 20;
	private static final BigDecimal MAX_RANGE = BigDecimal.TEN.pow(200);
	private static final BigDecimal EPSILON = BigDecimal.valueOf(1E-8);

	private static final Locale LOCALE = new Locale("en", "US");
	private static final NumberFormat CURRENCY_FORMATTER = NumberFormat
			.getCurrencyInstance(LOCALE);

	/**
	 * <p>
	 * <a href="https://w.wiki/9N4M">Newton's method</a> to try to find the zero
	 * for a function by using the division of function(x) by its derivative(x)
	 * </p>
	 * 
	 * @param function
	 *            the function
	 * @param derivative
	 *            the derivative of the function
	 * @return the value where the function equals approximatively zero
	 */
	public static Optional<BigDecimal> findZeroNewtonMethod(
			Function<BigDecimal, BigDecimal> function,
			Function<BigDecimal, BigDecimal> derivative) {

		// variables init.
		BigDecimal x = BigDecimal.ZERO;
		int iterationsCounter = 0;
		LinkedHashMap<Integer, BigDecimal> iterations = new LinkedHashMap<Integer, BigDecimal>();

		do {
			// Store each iteration in a list
			iterations.put(iterationsCounter, x);

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

		LOGGER.info("Find zero for the value of " + x + " after "
				+ iterationsCounter + " iterations");
		LOGGER.info("Iterations: " + iterations);

		return Optional.of(x);
	}

	private static boolean errorIsLargerThanExpected(
			Function<BigDecimal, BigDecimal> function, BigDecimal rate,
			BigDecimal epsilon) {
		return function.apply(rate).abs().compareTo(epsilon) >= 0;
	}

	/**
	 * <p>
	 * <b>This function allows to calculate YTM effective rates as APR, IRR
	 * based on the cashflow you provide</b>
	 * </p>
	 * <p>
	 * Rate calculation as described in the wiki page
	 * <a href="https://w.wiki/CwtN">Annual percentage rate</a>
	 * </p>
	 * <p>
	 * This fonction uses the <a href="https://w.wiki/9N4M">Newton's method</a>
	 * for best performance
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
	public static BigDecimal effectiveRateFromCashFlow(
			LinkedHashMap<LocalDate, BigDecimal> cashFlow) {

		if (cashFlow == null || cashFlow.isEmpty()) {
			return BigDecimal.ZERO;
		}

		BigDecimal rate = FinancialCalculation
				.findZeroNewtonMethod(x -> cashFlowSum(cashFlow, x),
						x -> cashFlowSumDerivative(cashFlow, x))
				.orElseThrow(() -> new UnsupportedOperationException(
						"Can't find YTM rate for cash flow "
								+ cashFlow.toString()))
				.multiply(BigDecimal.valueOf(100));

		LOGGER.info("Find the rate " + rate);

		MathContext m = new MathContext(6); // 6 precision
		return rate.round(m);
	}

	private static BigDecimal cashFlowSum(
			LinkedHashMap<LocalDate, BigDecimal> cashFlow, BigDecimal rate) {

		LOGGER.debug("Parameters: " + cashFlow + " / " + rate);

		// Determine the start date of the cashflow
		LocalDate startDate = cashFlow.entrySet().stream()
				.min(Comparator.comparing(Map.Entry::getKey)).get().getKey();

		BigDecimal sum = cashFlow.entrySet().stream()
				.map(entry -> discountAmount(startDate, entry, rate))
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		LOGGER.info(
				"Sum of discounted cashFlow:" + CURRENCY_FORMATTER.format(sum));

		return sum;
	}

	private static BigDecimal cashFlowSumDerivative(
			LinkedHashMap<LocalDate, BigDecimal> cashFlow, BigDecimal rate) {

		LOGGER.debug("Parameters: " + cashFlow + " / " + rate);

		// Determine the start date of the cashflow
		LocalDate startDate = cashFlow.entrySet().stream()
				.min(comparingByKey(LocalDate::compareTo)).get().getKey();

		BigDecimal sum = cashFlow.entrySet().stream().map(entry -> entry
				.getValue()
				.multiply(BigDecimal.valueOf(Math.pow(1 + rate.doubleValue(),
						-daysBetweenPayments(startDate, entry) / 365.0 - 1)))
				.multiply(BigDecimal
						.valueOf(-daysBetweenPayments(startDate, entry))
						.divide(BigDecimal.valueOf(365.0), SCALE,
								RoundingMode.HALF_UP)))
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		LOGGER.info("Sum of derivative discounted cashFlow:"
				+ CURRENCY_FORMATTER.format(sum));

		return sum;
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
	public static BigDecimal discountAmount(LocalDate startDate,
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
