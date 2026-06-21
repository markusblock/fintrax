package org.fintrax.config;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

public class I18n {
    private static final String BUNDLE_NAME = "messages";
    private static Locale currentLocale = Locale.ENGLISH;
    private static ResourceBundle bundle;

    public static void setLocale(Locale locale) {
        currentLocale = locale;
        bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale);
    }

    public static String get(String key) {
        if (bundle == null) {
            setLocale(Locale.ENGLISH);
        }
        try {
            return bundle.getString(key);
        } catch (Exception e) {
            return "!" + key + "!";
        }
    }

    public static String get(String key, Object... args) {
        String pattern = get(key);
        return MessageFormat.format(pattern, args);
    }

    public static Locale getCurrentLocale() {
        return currentLocale;
    }

    public static ResourceBundle getResourceBundle() {
        if (bundle == null) {
            setLocale(Locale.ENGLISH);
        }
        return bundle;
    }
}
