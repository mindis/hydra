/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.addthis.hydra.data.util;

import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;

/**
 * date helper that understands the {{now}} {{now+offset}} {{now-offset}} syntax.
 */
public final class DateUtil {

    public static final String NOW_PREFIX = "{{now";
    public static final String NOW_POSTFIX = "}}";
    public static final String OFF = "{{off}}";
    public static final String NOW = NOW_PREFIX + NOW_POSTFIX;


    @VisibleForTesting
    static final DateTimeFormatter ymdFormatter = new DateTimeFormatterBuilder().appendTwoDigitYear(2000)
                                                                                .appendMonthOfYear(2)
                                                                                .appendDayOfMonth(2).toFormatter();
    @VisibleForTesting
    static final DateTimeFormatter ymdhFormatter = new DateTimeFormatterBuilder().appendTwoDigitYear(2000)
                                                                                 .appendMonthOfYear(2)
                                                                                 .appendDayOfMonth(2)
                                                                                 .appendHourOfDay(2).toFormatter();
    private static final Pattern DATE_MACRO_PATTERN = Pattern.compile(
            // prefix literal
            Pattern.quote("{{now") +
            // optional increment or decrement adjustment
            "(([+-]\\d+)(h)?)?" +
            // optional date time format
            "(::.+?)?" +
            // optional timezone
            "(:.+?)?" +
            // suffix literal
            Pattern.quote("}}"));

    public static DateTime getDateTime(String format, String date) {
        return getDateTime(getFormatter(format), date, false);
    }

    public static DateTime getDateTime(DateTimeFormatter formatter, String date) {
        return getDateTime(formatter, date, false);
    }

    public static DateTime getDateTime(DateTimeFormatter formatter, String date, boolean hourly) {
        if (date.startsWith(NOW_PREFIX) && date.endsWith(NOW_POSTFIX)) {
            if (date.equals(NOW)) {
                return new DateTime();
            }
            DateTime time = new DateTime();
            int pos;
            if ((pos = date.indexOf(":")) > 0) {
                String timeZone = date.substring(pos + 1, date.length() - NOW_POSTFIX.length());
                DateTimeZone zone = DateTimeZone.forID(timeZone);
                time = time.withZone(zone);
                date = date.substring(0, pos) + NOW_POSTFIX;
            }
            if ((pos = date.indexOf("+")) > 0) {
                int offset = Integer.parseInt(date.substring(pos + 1, date.length() - NOW_POSTFIX.length()));
                time = hourly ? time.plusHours(offset) : time.plusDays(offset);
            } else if ((pos = date.indexOf("-")) > 0) {
                int offset = Integer.parseInt(date.substring(pos + 1, date.length() - NOW_POSTFIX.length()));
                time = hourly ? time.minusHours(offset) : time.minusDays(offset);
            }
            return time;
        }
        return formatter.parseDateTime(date);
    }

    public static String format(String format, long time) {
        return format(getFormatter(format), time);
    }

    public static String format(DateTimeFormatter formatter, long time) {
        return formatter.print(time);
    }

    public static DateTimeFormatter getFormatter(String format) {
        return DateTimeFormat.forPattern(format);
    }

    public static Iterator<DateTime> getDayIterator(final DateTime start, final DateTime end) {
        return new DateIterator(start, end, false);
    }

    public static Iterator<DateTime> getHourIterator(final DateTime start, final DateTime end) {
        return new DateIterator(start, end, true);
    }

    /** */
    private static class DateIterator implements Iterator<DateTime> {

        private final boolean reverse;
        private final boolean hourly;
        private DateTime start;
        private DateTime end;

        DateIterator(DateTime start, DateTime end, boolean hourly) {
            reverse = start.compareTo(end) > 0;
            this.hourly = hourly;
            this.start = start;
            this.end = end;
        }

        @Override
        public boolean hasNext() {
            return reverse ? start.compareTo(end) > 0 : end.compareTo(start) > 0;
        }

        @Override
        public DateTime next() {
            DateTime ret = start;
            if (reverse) {
                if (hourly) {
                    start.minusHours(1);
                } else {
                    start.plusHours(1);
                }
            } else {
                if (hourly) {
                    start.minusDays(1);
                } else {
                    start.plusDays(1);
                }
            }
            return ret;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Split a path up and replace any {{now-1}}-style elements with the YYMMDD equivalent.
     * {{now-1h}} subtracts one hour from the current time and returns a YYMMDDHH formatted string.
     *
     * @param path The input path to process
     * @return The path with the relevant tokens replaced
     */
    public static String expandDateMacro(String path) {
        StringBuffer sb = new StringBuffer();
        Matcher matcher = DATE_MACRO_PATTERN.matcher(path);
        while (matcher.find()) {
            DateTimeFormatter formatter = null;
            boolean hourly = false;
            String match;
            if (matcher.group(4) != null) {
                formatter = DateTimeFormat.forPattern(matcher.group(4).substring(2));
            }
            if (matcher.group(3) != null) {
                match = "{{now" + matcher.group(2) + Strings.nullToEmpty(matcher.group(5)) + "}}";
                hourly = true;
                formatter = (formatter != null) ? formatter : ymdhFormatter;
            } else {
                match = "{{now" + matcher.group(1) + Strings.nullToEmpty(matcher.group(5)) + "}}";
                formatter = (formatter != null) ? formatter : ymdFormatter;
            }
            matcher.appendReplacement(sb, com.addthis.hydra.data.util.DateUtil.getDateTime(formatter, match, hourly).toString(formatter));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
