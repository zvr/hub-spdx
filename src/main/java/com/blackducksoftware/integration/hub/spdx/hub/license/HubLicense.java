/**
 * hub-spdx
 *
 * Copyright (C) 2018 Black Duck Software, Inc.
 * http://www.blackducksoftware.com/
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.blackducksoftware.integration.hub.spdx.hub.license;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.blackducksoftware.integration.exception.IntegrationException;
import com.blackducksoftware.integration.hub.api.view.MetaHandler;
import com.blackducksoftware.integration.hub.spdx.hub.Hub;
import com.blackducksoftware.integration.log.Slf4jIntLogger;

@Component
public class HubLicense {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${retry.count:5}")
    private int retryCount;

    @Autowired
    private Hub hub;

    public SpdxIdAwareLicenseView getLicenseView(final Optional<String> licenseViewUrl) throws IntegrationException {
        if (!licenseViewUrl.isPresent()) {
            return null;
        }
        SpdxIdAwareLicenseView licenseView = getLicenseViewSingleLevel(licenseViewUrl.get());
        // TODO the link WAS: MetaHandler.LICENSE_LINK, which seems to be gone now. May belong in LicenseView?
        final String embeddedLicenseUrl = new MetaHandler(new Slf4jIntLogger(logger)).getFirstLinkSafely(licenseView, "license");
        logger.debug(String.format("Embedded license URL: %s", embeddedLicenseUrl));
        if (!StringUtils.isBlank(embeddedLicenseUrl)) {
            logger.debug(String.format("Found embedded license URL: %s; fetching that licenseView", embeddedLicenseUrl));
            try {
                licenseView = getLicenseViewSingleLevel(embeddedLicenseUrl);
            } catch (final IntegrationException e) {
                logger.debug(String.format("Unable to get license for embedded license URL: %s", embeddedLicenseUrl));
            }
        }
        return licenseView;
    }

    private SpdxIdAwareLicenseView getLicenseViewSingleLevel(final String licenseViewUrl) throws IntegrationException {
        if (licenseViewUrl == null) {
            return null;
        }
        logger.trace(String.format("before hub.getLicenseDataService().getLicenseView(%s)", licenseViewUrl));
        SpdxIdAwareLicenseView licenseView = null;
        for (int i = 0; i < retryCount; i++) {
            try {
                licenseView = hub.getLicenseService().getLicenseView(licenseViewUrl);
                break;
            } catch (final IntegrationException e) {
                logger.warn(String.format("Attempt #%d of %d: Error getting license %s from Hub: %s", i + 1, retryCount, licenseViewUrl, e.getMessage()));
            }
        }
        if (licenseView == null) {
            throw new IntegrationException(String.format("Exceeded retry count (%d) trying to get: %s", retryCount, licenseViewUrl));
        }
        logger.debug(String.format("License SPDX ID from Hub: %s", licenseView.spdxId));
        return licenseView;
    }

    public String getLicenseText(final SpdxIdAwareLicenseView licenseView) throws IntegrationException {
        logger.trace("before hub.getLicenseDataService().getLicenseText(licenseView)");
        final String licenseText = hub.getLicenseService().getLicenseText(licenseView);
        logger.trace("after hub.getLicenseDataService().getLicenseText(licenseView)");
        return licenseText;
    }
}
