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

	@Test
	void rateSimple() {

		Locale locale = new Locale("en", "US");
		NumberFormat currencyFormatter = NumberFormat
				.getCurrencyInstance(locale);

		BigDecimal rate = FinancialCalculation.calculateYTM(null);
		LOGGER.info("Rate when null cashflow: " + rate.doubleValue());
		assertEquals(0.0, rate.doubleValue());

		LinkedHashMap<LocalDate, BigDecimal> cashflow = new LinkedHashMap<LocalDate, BigDecimal>();
		rate = FinancialCalculation.calculateYTM(cashflow);
		LOGGER.info("Rate when empty cashflow: " + rate.doubleValue());
		assertEquals(0.0, rate.doubleValue());

		LocalDate today = LocalDate.now();
		cashflow.put(today, new BigDecimal(-100000.0));
		cashflow.put(today.plusYears(1), new BigDecimal(120000.0));
		rate = FinancialCalculation.calculateYTM(cashflow);
		LOGGER.info("Rate with a very simple annual cashflow: "
				+ rate.doubleValue());
		assertEquals(20.0, rate.doubleValue());

		cashflow.clear();
		cashflow.put(today, new BigDecimal(-100000.0));
		cashflow.put(today.plusMonths(6), new BigDecimal(110000.0));
		rate = FinancialCalculation.calculateYTM(cashflow);
		LOGGER.info("Rate with a very simple 6 months cashflow: "
				+ rate.doubleValue());
		assertEquals(21.1913, rate.doubleValue());

		cashflow.clear();
		LocalDate creditDate = LocalDate.of(2020, 1, 1);
		LocalDate instDate;
		double creditAmount = 2000000.0;
		cashflow.put(creditDate, new BigDecimal(-creditAmount));
		LOGGER.info("Credit amount (" + creditDate + ") = "
				+ currencyFormatter.format(creditAmount));
		// classic loan
		double instAmount = 39602.40;
		int i;
		for (i = 1; i <= 60; i++) {
			instDate = creditDate.plusMonths(i);
			LOGGER.info("instalment #" + i + " (" + instDate + ") = "
					+ currencyFormatter.format(instAmount));
			cashflow.put(instDate, new BigDecimal(instAmount));
		}
		rate = FinancialCalculation.calculateYTM(cashflow);
		i--;
		LOGGER.info("Rate with a " + i + " months loan: " + rate.doubleValue());
		assertEquals(7.22609, rate.doubleValue());

		cashflow.clear();
		creditDate = LocalDate.of(2020, 1, 1);
		creditAmount = 300000.0;
		cashflow.put(creditDate, new BigDecimal(-creditAmount));
		LOGGER.info("Credit amount (" + creditDate + ") = "
				+ currencyFormatter.format(creditAmount));

		// classic loan
		instAmount = 2934.36;
		for (i = 1; i <= 120; i++) {
			instDate = creditDate.plusMonths(i);
			LOGGER.info("instalment #" + i + " (" + instDate + ") = "
					+ currencyFormatter.format(instAmount));
			cashflow.put(instDate, new BigDecimal(instAmount));
		}
		rate = FinancialCalculation.calculateYTM(cashflow);
		i--;
		LOGGER.info("Rate with a " + i + " months loan: " + rate.doubleValue());
		assertEquals(3.31767, rate.doubleValue());

	}

}
