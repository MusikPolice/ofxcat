package ca.jonathanfritz.ofxcat.utils;

public class StringUtils {

    public static String coerceNullableString(String value) {
        return org.apache.commons.lang3.StringUtils.isNotBlank(value) ? value.trim() : "";
    }

}
