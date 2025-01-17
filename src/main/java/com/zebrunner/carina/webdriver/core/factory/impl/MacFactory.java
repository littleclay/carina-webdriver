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
package com.zebrunner.carina.webdriver.core.factory.impl;

import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URL;

import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zebrunner.carina.utils.Configuration;
import com.zebrunner.carina.webdriver.core.capability.impl.mac.Mac2Capabilities;
import com.zebrunner.carina.webdriver.core.factory.AbstractFactory;
import com.zebrunner.carina.webdriver.listener.EventFiringAppiumCommandExecutor;

import io.appium.java_client.mac.Mac2Driver;

/**
 * MacFactory creates instance {@link WebDriver} for Mac native application testing.
 * 
 * @author Sergei Zagriychuk (sergeizagriychuk@gmail.com)
 */
public class MacFactory extends AbstractFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Override
    public WebDriver create(String name, MutableCapabilities capabilities, String seleniumHost) {
        if (seleniumHost == null) {
            seleniumHost = Configuration.getSeleniumUrl();
        }
        LOGGER.debug("Selenium URL: {}", seleniumHost);

        if (isCapabilitiesEmpty(capabilities)) {
            capabilities = new Mac2Capabilities().getCapability(name);
        }

        LOGGER.debug("Capabilities: {}", capabilities);

        try {
            EventFiringAppiumCommandExecutor ce = new EventFiringAppiumCommandExecutor(new URL(seleniumHost));
            return new Mac2Driver(ce, capabilities);
        } catch (MalformedURLException e) {
            throw new UncheckedIOException("Malformed appium URL!", e);
        }
    }
}
