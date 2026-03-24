package com.billme.util;

import java.math.BigDecimal;

public class NumberToWords {

    public static String convert(BigDecimal amount) {

        long rupees = amount.longValue();
        int paise = amount.subtract(new BigDecimal(rupees))
                .multiply(new BigDecimal(100))
                .intValue();

        String result = "";

        if (rupees > 0) {
            result += convertToWords(rupees) + " Rupees";
        }

        if (paise > 0) {
            result += " and " + convertToWords(paise) + " Paise";
        }

        if (result.isEmpty()) {
            return "Zero Rupees Only";
        }

        return result + " Only";
    }

    private static String convertToWords(long num) {

        String[] units = {"", "One", "Two", "Three", "Four", "Five",
                "Six", "Seven", "Eight", "Nine", "Ten", "Eleven",
                "Twelve", "Thirteen", "Fourteen", "Fifteen",
                "Sixteen", "Seventeen", "Eighteen", "Nineteen"};

        String[] tens = {"", "", "Twenty", "Thirty", "Forty",
                "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

        if (num < 20) return units[(int) num];

        if (num < 100)
            return tens[(int) num / 10] + " " + units[(int) num % 10];

        if (num < 1000)
            return units[(int) num / 100] + " Hundred " +
                    convertToWords(num % 100);

        if (num < 100000)
            return convertToWords(num / 1000) + " Thousand " +
                    convertToWords(num % 1000);

        if (num < 10000000)
            return convertToWords(num / 100000) + " Lakh " +
                    convertToWords(num % 100000);

        return convertToWords(num / 10000000) + " Crore " +
                convertToWords(num % 10000000);
    }
}