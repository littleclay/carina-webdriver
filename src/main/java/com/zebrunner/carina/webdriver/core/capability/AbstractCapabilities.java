/*******************************************************************************
 * Copyright 2020-2022 Zebrunner Inc (https://www.zebrunner.com).
 *
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
 *******************************************************************************/
package com.zebrunner.carina.webdriver.core.capability;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.MutablePair;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.remote.CapabilityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zebrunner.carina.proxy.ProxyUtils;
import com.zebrunner.carina.utils.Configuration;
import com.zebrunner.carina.utils.Configuration.Parameter;
import com.zebrunner.carina.utils.R;
import com.zebrunner.carina.utils.commons.SpecialKeywords;
import com.zebrunner.carina.utils.exception.InvalidConfigurationException;

import io.appium.java_client.remote.MobileCapabilityType;

public abstract class AbstractCapabilities<T extends MutableCapabilities> {
    // TODO: [VD] reorganize in the same way Firefox profiles args/options if any and review other browsers
    // support customization for Chrome args and options

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final Pattern CAPABILITY_WITH_TYPE_PATTERN = Pattern.compile("^(?<name>.+)(?<type>\\[.+\\])$");

    /**
     * Get capabilities from the configuration ({@link R#CONFIG}).
     * Additional capabilities can also be added (depends on implementation).
     *
     * @return see {@link T}
     */
    public abstract T getCapability(String testName);

    /**
     * Add proxy capability. Should only be used for Selenium session only.
     *
     * @param capabilities see {@link T}
     */
    protected void addProxy(T capabilities) {
        ProxyUtils.getSeleniumProxy()
                .ifPresent(proxy -> capabilities.setCapability(CapabilityType.PROXY, proxy));
    }

    /**
     * Add capabilities from configuration {@link R#CONFIG}.
     *
     * @param options see {@link T}
     */
    protected void addConfigurationCapabilities(T options) {
        addPropertiesCapabilities(options, R.CONFIG.getProperties());
    }

    /**
     * Add capabilities from properties
     *
     * @param options see {@link C}
     * @param props see {@link Properties}
     */
    static <C extends MutableCapabilities> void addPropertiesCapabilities(C options, Properties props) {
        @SuppressWarnings({ "unchecked", "rawtypes" })
        Map<String, String> properties = new HashMap(props);
        Map<String, Object> capabilities = properties.entrySet()
                .stream()
                .filter(entry -> entry.getKey().startsWith("capabilities."))
                .filter(entry -> entry.getValue() != null)
                .map(entry -> {
                    MutablePair<String, String> pair = new MutablePair<>();
                    pair.setLeft(entry.getKey().replaceFirst("capabilities.", ""));
                    pair.setRight(entry.getValue());
                    return pair;
                })
                .map(p -> parseCapabilityType(p.getLeft(), p.getRight()))
                .collect(Collectors.toMap(MutablePair::getLeft, MutablePair::getRight));

        for (Map.Entry<String, Object> entry : capabilities.entrySet()) {
            List<String> names = Arrays.asList(entry.getKey().split("\\."));

            // TODO add support of any nesting
            if (names.isEmpty()) {
                // should never happen
                throw new RuntimeException("Something went wrong when try to create capabilities from configuration.");
            } else if (names.size() == 1) {
                options.setCapability(names.get(0), entry.getValue());
            } else if (names.size() == 2) {
                HashMap<String, Object> nestCapability = new HashMap<>();
                if (options.getCapability(names.get(0)) != null) {
                    // If we already have inner capability, we think that it is HashMap<String, Object> (custom capabilities)
                    nestCapability = (HashMap<String, Object>) options.getCapability(names.get(0));
                }

                nestCapability.put(names.get(1), entry.getValue());
                options.setCapability(names.get(0), nestCapability);
            } else if (names.size() == 3) {
                HashMap<String, Object> nestCapability = new HashMap<>();
                HashMap<String, Object> secondNestCapability = new HashMap<>();

                if (options.getCapability(names.get(0)) != null) {
                    // If we already have inner capability, we think that it is HashMap<String, Object>
                    nestCapability = (HashMap<String, Object>) options.getCapability(names.get(0));
                    if (nestCapability.containsKey(names.get(1))) {
                        secondNestCapability = (HashMap<String, Object>) nestCapability.get(names.get(1));
                    }
                }
                secondNestCapability.put(names.get(2), entry.getValue());
                nestCapability.put(names.get(1), secondNestCapability);
                options.setCapability(names.get(0), nestCapability);
            } else {
                // Let's hope it won't be needed.
                throw new UnsupportedOperationException("At the moment nesting of more than 3 capabilities is not supported. "
                        + "If you come across a situation in which this is necessary, please notify the Carina Support team.");
            }
        }
    }

    /**
     * Parse capability type.<br>
     * Result type depends on:
     * 1. If name of the capability ends with [string], [boolean] or [integer], then the capability value will be cast to it.
     * 2. If we have no information about type in capability name, result value depends on value.
     *
     * @param capabilityName name of the capability, for example {@code platformName} or {zebrunner:options.enableVideo[boolean]}
     * @param capabilityValue capability value. Since we take it from the configuration file, it is immediately of type String
     * @return {@link MutablePair}, where left is the capability name and right is the value
     */
    static MutablePair<String, Object> parseCapabilityType(String capabilityName, String capabilityValue) {
        MutablePair<String, Object> pair = new MutablePair<>();
        Matcher matcher = CAPABILITY_WITH_TYPE_PATTERN.matcher(capabilityName);
        if (matcher.find()) {
            String name = matcher.group("name");
            String type = matcher.group("type");
            Object value = null;
            if ("[string]".equalsIgnoreCase(type)) {
                value = capabilityValue;
            } else if ("[boolean]".equalsIgnoreCase(type)) {
                if ("true".equalsIgnoreCase(capabilityValue)) {
                    value = true;
                } else if ("false".equalsIgnoreCase(capabilityValue)) {
                    value = false;
                } else {
                    throw new InvalidConfigurationException(
                            String.format("Provided boolean type for '%s' capability, but it is not contains true or false value.", name));
                }
            } else if ("[integer]".equalsIgnoreCase(type)) {
                try {
                    value = Integer.parseInt(capabilityValue);
                } catch (NumberFormatException e) {
                    throw new InvalidConfigurationException(
                            String.format("Provided integer type for '%s' capability, but it is not contains integer value.", name));
                }
            } else {
                throw new InvalidConfigurationException(String.format("Unsupported '%s' type of '%s' capability.", type, name));
            }
            pair.setLeft(name);
            pair.setRight(value);
        } else {
            pair.setLeft(capabilityName);
            if (isNumber(capabilityValue)) {
                pair.setRight(Integer.parseInt(capabilityValue));
            } else if ("true".equalsIgnoreCase(capabilityValue)) {
                pair.setRight(true);
            } else if ("false".equalsIgnoreCase(capabilityValue)) {
                pair.setRight(false);
            } else {
                pair.setRight(capabilityValue);
            }
        }
        return pair;
    }

    protected static boolean isNumber(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return false;
        }
        return true;
    }

    /**
     * Add locale and language capabilities to caps param
     */
    protected T setLocaleAndLanguage(T caps) {
        /*
         * http://appium.io/docs/en/writing-running-appium/caps/ locale and language
         * Locale to set for iOS (XCUITest driver only) and Android.
         * fr_CA format for iOS. CA format (country name abbreviation) for Android
         */
        // parse locale param as it has language and country by default like en_US
        String localeValue = Configuration.get(Parameter.LOCALE);
        LOGGER.debug("Default locale value is : {}", localeValue);
        String[] values = localeValue.split("_");
        if (values.length == 1) {
            // only locale is present!
            caps.setCapability(MobileCapabilityType.LOCALE, localeValue);
            String langValue = Configuration.get(Parameter.LANGUAGE);
            if (!langValue.isEmpty()) {
                LOGGER.debug("Default language value is : {}", langValue);
                // provide extra capability language only if it exists among config parameters...
                caps.setCapability(MobileCapabilityType.LANGUAGE, langValue);
            }
        } else if (values.length == 2) {
            if (Configuration.getPlatform().equalsIgnoreCase(SpecialKeywords.ANDROID)) {
                LOGGER.debug("Put language and locale to android capabilities. language: {}; locale: {}", values[0], values[1]);
                caps.setCapability(MobileCapabilityType.LANGUAGE, values[0]);
                caps.setCapability(MobileCapabilityType.LOCALE, values[1]);
            } else if (Configuration.getPlatform().equalsIgnoreCase(SpecialKeywords.IOS)) {
                LOGGER.debug("Put language and locale to iOS capabilities. language: {}; locale: {}", values[0], localeValue);
                caps.setCapability(MobileCapabilityType.LANGUAGE, values[0]);
                caps.setCapability(MobileCapabilityType.LOCALE, localeValue);
            }
        } else {
            LOGGER.error("Undefined locale provided (ignoring for mobile capabilitites): {}", localeValue);
        }
        return caps;
    }
}
