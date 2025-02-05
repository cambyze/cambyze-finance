package com.cambyze.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class FinancialCalculationTests {

	private static final Logger LOGGER = LoggerFactory
			.getLogger(FinancialCalculationTests.class);
	private static final Locale LOCALE = new Locale("en", "US");
	private static final NumberFormat CURRENCY_FORMATTER = NumberFormat
			.getCurrencyInstance(LOCALE);

	private static final LocalDate TODAY = LocalDate.now();

	@Test
	void rateSimple() {

		LinkedHashMap<LocalDate, BigDecimal> cashflow = new LinkedHashMap<LocalDate, BigDecimal>();
		BigDecimal rate;

		rate = FinancialCalculation.effectiveRateFromCashFlow(null);
		LOGGER.info("Rate when null cashflow: " + rate.doubleValue());
		assertEquals(0.0, rate.doubleValue());

		rate = FinancialCalculation.effectiveRateFromCashFlow(cashflow);
		LOGGER.info("Rate when empty cashflow: " + rate.doubleValue());
		assertEquals(0.0, rate.doubleValue());

		cashflow.clear();
		cashflow.put(TODAY, new BigDecimal(-100000.0));
		cashflow.put(TODAY.plusYears(1), new BigDecimal(120000.0));
		rate = FinancialCalculation.effectiveRateFromCashFlow(cashflow);
		LOGGER.info("Rate with a very simple annual cashflow: "
				+ rate.doubleValue());
		assertEquals(20.0, rate.doubleValue());

		cashflow.clear();
		cashflow.put(TODAY, new BigDecimal(-100000.0));
		cashflow.put(TODAY.plusMonths(6), new BigDecimal(110000.0));
		rate = FinancialCalculation.effectiveRateFromCashFlow(cashflow);
		LOGGER.info("Rate with a very simple 6 months cashflow: "
				+ rate.doubleValue());
		assertEquals(21.1913, rate.doubleValue());
	}

	@Test
	void rate60MonthsLoan() {

		LinkedHashMap<LocalDate, BigDecimal> cashflow = new LinkedHashMap<LocalDate, BigDecimal>();
		BigDecimal rate;
		LocalDate creditDate;
		LocalDate instDate;
		double creditAmount;
		double instAmount;
		int i;

		cashflow.clear();
		creditDate = LocalDate.of(2020, 1, 1);
		creditAmount = 2000000.0;
		cashflow.put(creditDate, new BigDecimal(-creditAmount));
		LOGGER.debug("Credit amount (" + creditDate + ") = "
				+ CURRENCY_FORMATTER.format(creditAmount));
		// classic loan
		instAmount = 39602.40;
		for (i = 1; i <= 60; i++) {
			instDate = creditDate.plusMonths(i);
			LOGGER.debug("instalment #" + i + " (" + instDate + ") = "
					+ CURRENCY_FORMATTER.format(instAmount));
			cashflow.put(instDate, new BigDecimal(instAmount));
		}
		rate = FinancialCalculation.effectiveRateFromCashFlow(cashflow);
		i--;
		LOGGER.info("Rate with a " + i + " months loan: " + rate.doubleValue());
		assertEquals(7.22609, rate.doubleValue());
	}

	@Test
	void rate120MonthsLoan() {

		LinkedHashMap<LocalDate, BigDecimal> cashflow = new LinkedHashMap<LocalDate, BigDecimal>();
		BigDecimal rate;
		LocalDate creditDate;
		LocalDate instDate;
		double creditAmount;
		double instAmount;
		int i;

		cashflow.clear();
		creditDate = LocalDate.of(2020, 1, 1);
		creditAmount = 300000.0;
		cashflow.put(creditDate, new BigDecimal(-creditAmount));
		LOGGER.debug("Credit amount (" + creditDate + ") = "
				+ CURRENCY_FORMATTER.format(creditAmount));

		// classic loan
		instAmount = 2934.36;
		for (i = 1; i <= 120; i++) {
			instDate = creditDate.plusMonths(i);
			LOGGER.debug("instalment #" + i + " (" + instDate + ") = "
					+ CURRENCY_FORMATTER.format(instAmount));
			cashflow.put(instDate, new BigDecimal(instAmount));
		}
		rate = FinancialCalculation.effectiveRateFromCashFlow(cashflow);
		i--;
		LOGGER.info("Rate with a " + i + " months loan: " + rate.doubleValue());
		assertEquals(3.31767, rate.doubleValue());
	}
}
