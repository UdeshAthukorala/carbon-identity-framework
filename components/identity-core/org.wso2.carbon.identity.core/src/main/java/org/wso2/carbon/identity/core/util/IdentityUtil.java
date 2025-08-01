/*
 * Copyright (c) 2005-2025, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.core.util;

import com.ibm.wsdl.util.xml.DOM2Writer;
import org.apache.axiom.om.OMElement;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.httpclient.HttpURL;
import org.apache.commons.httpclient.HttpsURL;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xerces.impl.Constants;
import org.apache.xerces.util.SecurityManager;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.wso2.carbon.CarbonConstants;
import org.wso2.carbon.CarbonException;
import org.wso2.carbon.base.MultitenantConstants;
import org.wso2.carbon.base.ServerConfiguration;
import org.wso2.carbon.caching.impl.CachingConstants;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.core.util.AdminServicesUtil;
import org.wso2.carbon.core.util.KeyStoreManager;
import org.wso2.carbon.core.util.SignatureUtil;
import org.wso2.carbon.core.util.Utils;
import org.wso2.carbon.identity.base.IdentityConstants;
import org.wso2.carbon.identity.base.IdentityException;
import org.wso2.carbon.identity.base.IdentityRuntimeException;
import org.wso2.carbon.identity.core.IdentityKeyStoreResolver;
import org.wso2.carbon.identity.core.internal.component.IdentityCoreServiceComponent;
import org.wso2.carbon.identity.core.internal.component.IdentityCoreServiceDataHolder;
import org.wso2.carbon.identity.core.model.IdentityCacheConfig;
import org.wso2.carbon.identity.core.model.IdentityCacheConfigKey;
import org.wso2.carbon.identity.core.model.IdentityCookieConfig;
import org.wso2.carbon.identity.core.model.IdentityErrorMsgContext;
import org.wso2.carbon.identity.core.model.IdentityEventListenerConfig;
import org.wso2.carbon.identity.core.model.IdentityEventListenerConfigKey;
import org.wso2.carbon.identity.core.model.LegacyFeatureConfig;
import org.wso2.carbon.identity.core.model.ReverseProxyConfig;
import org.wso2.carbon.identity.organization.management.service.exception.OrganizationManagementException;
import org.wso2.carbon.registry.core.utils.UUIDGenerator;
import org.wso2.carbon.user.api.RealmConfiguration;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.UserRealm;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.UserStoreManager;
import org.wso2.carbon.user.core.common.AbstractUserStoreManager;
import org.wso2.carbon.user.core.common.User;
import org.wso2.carbon.user.core.util.UserCoreUtil;
import org.wso2.carbon.utils.CarbonUtils;
import org.wso2.carbon.utils.NetworkUtils;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.SocketException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;

import static org.wso2.carbon.identity.core.util.IdentityCoreConstants.AGENT_IDENTITY_ENABLE;
import static org.wso2.carbon.identity.core.util.IdentityCoreConstants.AGENT_IDENTITY_USERSTORE_NAME;
import static org.wso2.carbon.identity.core.util.IdentityCoreConstants.DEFAULT_AGENT_IDENTITY_USERSTORE_NAME;
import static org.wso2.carbon.identity.core.util.IdentityCoreConstants.ALPHABET;
import static org.wso2.carbon.identity.core.util.IdentityCoreConstants.ENCODED_ZERO;
import static org.wso2.carbon.identity.core.util.IdentityCoreConstants.INDEXES;
import static org.wso2.carbon.identity.core.util.IdentityCoreConstants.USERS_LIST_PER_ROLE_LOWER_BOUND;
import static org.wso2.carbon.identity.core.util.IdentityKeyStoreResolverConstants.ErrorMessages.ERROR_RETRIEVING_TENANT_CONTEXT_PUBLIC_CERTIFICATE_KEYSTORE_NOT_EXIST;

public class IdentityUtil {

    public static final ThreadLocal<Map<String, Object>> threadLocalProperties = new
            ThreadLocal<Map<String, Object>>() {
                @Override
                protected Map<String, Object> initialValue() {
                    return new HashMap<>();
                }
            };
    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";
    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";
    private static final String SHA1_ALGORITHM = "SHA1";
    private static final String SHA256_ALGORITHM = "SHA256";
    private final static char[] ppidDisplayCharMap = new char[]{'Q', 'L', '2', '3', '4', '5',
            '6', '7', '8', '9', 'A', 'B', 'C',
            'D', 'E', 'F', 'G', 'H', 'J', 'K',
            'M', 'N', 'P', 'R', 'S', 'T', 'U',
            'V', 'W', 'X', 'Y', 'Z'};
    public static final String DEFAULT_FILE_NAME_REGEX = "^(?!(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\.[^.]*)?$)" +
            "[^<>:\"/\\\\|?*\\x00-\\x1F]*[^<>:\"/\\\\|?*\\x00-\\x1F\\ .]$";
    private static final String ENABLE_RECOVERY_ENDPOINT = "EnableRecoveryEndpoint";
    private static final String ENABLE_SELF_SIGN_UP_ENDPOINT = "EnableSelfSignUpEndpoint";
    private static final String ENABLE_EMAIL_USERNAME = "EnableEmailUserName";
    private static final String DISABLE_EMAIL_USERNAME_VALIDATION = "DisableEmailUserNameValidation";
    private static Log log = LogFactory.getLog(IdentityUtil.class);
    private static Map<String, Object> configuration = new HashMap<>();
    private static Map<IdentityEventListenerConfigKey, IdentityEventListenerConfig> eventListenerConfiguration = new
            HashMap<>();
    private static Map<IdentityCacheConfigKey, IdentityCacheConfig> identityCacheConfigurationHolder = new HashMap<>();
    private static Map<String, IdentityCookieConfig> identityCookiesConfigurationHolder = new HashMap<>();
    private static Map<String, LegacyFeatureConfig> legacyFeatureConfigurationHolder = new HashMap<>();
    private static Map<String, ReverseProxyConfig> reverseProxyConfigurationHolder = new HashMap<>();
    private static List<String> cookiesToInvalidateConfigurationHolder = new ArrayList<>();
    private static Map<String, Boolean> storeProcedureBasedDAOConfigurationHolder = new HashMap<>();
    private static Document importerDoc = null;
    private static ThreadLocal<IdentityErrorMsgContext> IdentityError = new ThreadLocal<IdentityErrorMsgContext>();
    private static final int ENTITY_EXPANSION_LIMIT = 0;
    public static final String PEM_BEGIN_CERTFICATE = "-----BEGIN CERTIFICATE-----";
    public static final String PEM_END_CERTIFICATE = "-----END CERTIFICATE-----";
    private static final String APPLICATION_DOMAIN = "Application";
    private static final String WORKFLOW_DOMAIN = "Workflow";
    private static final String HTTP = "http";
    private static final String HTTPS = "https";
    private static Boolean groupsVsRolesSeparationImprovementsEnabled;
    private static Boolean showLegacyRoleClaimOnGroupRoleSeparationEnabled;
    private static String JAVAX_TRANSFORMER_PROP_VAL = "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl";

    // System Property for trust managers.
    public static final String PROP_TRUST_STORE_UPDATE_REQUIRED =
            "org.wso2.carbon.identity.core.util.TRUST_STORE_UPDATE_REQUIRED";


    /**
     * @return
     */
    public static IdentityErrorMsgContext getIdentityErrorMsg() {
        if (IdentityError.get() == null) {
            return null;
        }
        return IdentityError.get();
    }

    /**
     * @param error
     */
    public static void setIdentityErrorMsg(IdentityErrorMsgContext error) {
        IdentityError.set(error);
    }

    /**
     *
     */
    public static void clearIdentityErrorMsg() {
        IdentityError.remove();
    }

    /**
     * Read configuration elements from the identity.xml.
     *
     * @param key Element Name as specified from the parent elements in the XML structure.
     *            To read the element value of b in {@code <a><b>text</b></a>}, the property
     *            name should be passed as "a.b"
     * @return Element text value, "text" for the above element with the placeholders replaced.
     */
    public static String getProperty(String key) {

        String strValue = getPropertyValue(key);
        strValue = fillURLPlaceholders(strValue);
        return strValue;
    }

    /**
     * Read configuration elements from the identity.xml and
     * drops the port if it is 443 for https and 80 for http.
     *
     * @param key Element name as specified from the parent elements in the XML structure.
     *            To read the element value of b in {@code <a><b>text</b></a>}, the property
     *            name should be passed as "a.b".
     * @return The value of the element, which is "text" in the above element, with the placeholders
     * replaced and the standard port dropped.
     */
    public static String getPropertyWithoutStandardPort(String key) {

        String strValue = getPropertyValue(key);
        strValue = replacePortNumberPlaceholder(strValue, Boolean.TRUE);
        strValue = fillURLPlaceholders(strValue);
        return strValue;
    }

    /**
     * Gets the property value corresponding to the given key from the identity.xml file.
     *
     * @param key Element name as specified from the parent elements in the XML structure.
     *            To read the element value of b in {@code <a><b>text</b></a>}, the property
     *            name should be passed as "a.b".
     * @return The value of the element, which is "text" in the above element.
     */
    private static String getPropertyValue(String key) {

        Object value = configuration.get(key);
        String strValue;

        if (value == null) {
            return null;
        }
        if (value instanceof List) {
            value = ((List) value).get(0);
        }
        if (value instanceof String) {
            strValue = (String) value;
        } else {
            strValue = String.valueOf(value);
        }
        return strValue;
    }

    public static IdentityEventListenerConfig readEventListenerProperty(String type, String name) {
        IdentityEventListenerConfigKey identityEventListenerConfigKey = new IdentityEventListenerConfigKey(type, name);
        IdentityEventListenerConfig identityEventListenerConfig = eventListenerConfiguration.get(identityEventListenerConfigKey);
        return identityEventListenerConfig;
    }

    /**
     * This reads the &lt;CacheConfig&gt; configuration in identity.xml.
     * Since the name of the cache is different between the distributed mode and local mode,
     * that is specially handled.
     * <p>
     * When calling this method, only pass the cacheManagerName and cacheName parameters considering
     * how the names are set in a clustered environment i.e. without the CachingConstants.LOCAL_CACHE_PREFIX.
     */
    public static IdentityCacheConfig getIdentityCacheConfig(String cacheManagerName, String cacheName) {
        IdentityCacheConfigKey configKey = new IdentityCacheConfigKey(cacheManagerName, cacheName);
        IdentityCacheConfig identityCacheConfig = identityCacheConfigurationHolder.get(configKey);
        if (identityCacheConfig == null && cacheName.startsWith(CachingConstants.LOCAL_CACHE_PREFIX)) {
            configKey = new IdentityCacheConfigKey(cacheManagerName,
                    cacheName.replace(CachingConstants.LOCAL_CACHE_PREFIX, ""));
            identityCacheConfig = identityCacheConfigurationHolder.get(configKey);
        }
        return identityCacheConfig;
    }

    public static IdentityCookieConfig getIdentityCookieConfig(String cookieName) {
        return identityCookiesConfigurationHolder.get(cookieName);
    }


    public static Map<String, IdentityCookieConfig> getIdentityCookiesConfigurationHolder() {
        return identityCookiesConfigurationHolder;
    }

    public static List<String> getCookiesToInvalidateConfigurationHolder() {

        return cookiesToInvalidateConfigurationHolder;
    }

    public static Map<String, Boolean> getStoreProcedureBasedDAOConfigurationHolder() {

        return storeProcedureBasedDAOConfigurationHolder;
    }

    /**
     * This method can use to check whether the legacy feature for the given legacy feature id is enabled or not
     *
     * @param legacyFeatureId      Legacy feature id.
     * @param legacyFeatureVersion Legacy feature version.
     * @return Whether the legacy feature is enabled or not.
     */
    public static boolean isLegacyFeatureEnabled(String legacyFeatureId, String legacyFeatureVersion) {

        String legacyFeatureConfig;
        if (StringUtils.isBlank(legacyFeatureId)) {
            return false;
        }
        if (StringUtils.isBlank(legacyFeatureVersion)) {
            legacyFeatureConfig = legacyFeatureId.trim();
        } else {
            legacyFeatureConfig = legacyFeatureId.trim() + legacyFeatureVersion.trim();
        }
        if (StringUtils.isNotBlank(legacyFeatureConfig)) {
            LegacyFeatureConfig legacyFeatureConfiguration =
                    legacyFeatureConfigurationHolder.get(legacyFeatureConfig);
            if (legacyFeatureConfiguration != null && legacyFeatureConfiguration.isEnabled()) {
                if (log.isDebugEnabled()) {
                    log.debug("Legacy feature id: " + legacyFeatureConfiguration.getId() +
                            " legacy feature version : " + legacyFeatureConfiguration.getVersion() +
                            " is enabled: " + legacyFeatureConfiguration.isEnabled());
                }
                return legacyFeatureConfiguration.isEnabled();
            }

            if (log.isDebugEnabled()) {
                log.debug("Legacy feature is not configured or the configured legacy feature is empty. " +
                        "Hence returning false.");
            }
        }
        return false;
    }

    /**
     * Get the configured proxy context.
     *
     * @param defaultContext Default context.
     * @return The proxy context if it is configured else return the default context.
     */
    public static String getProxyContext(String defaultContext) {

        if (StringUtils.isNotBlank(defaultContext)) {
            ReverseProxyConfig reverseProxyConfig = reverseProxyConfigurationHolder.get(defaultContext);
            if (reverseProxyConfig != null && StringUtils.isNotBlank(reverseProxyConfig.getProxyContext())) {
                if (log.isDebugEnabled()) {
                    log.debug("Returning the proxy context: " + reverseProxyConfig.getProxyContext() +
                            " for the default context " + defaultContext);
                }
                return reverseProxyConfig.getProxyContext();
            }

            if (log.isDebugEnabled()) {
                log.debug("Proxy context is not configured or the configured proxy context is empty. " +
                        "Hence returning the default context: " + defaultContext);
            }
        }
        return defaultContext;
    }

    public static void populateProperties() {
        configuration = IdentityConfigParser.getInstance().getConfiguration();
        eventListenerConfiguration = IdentityConfigParser.getInstance().getEventListenerConfiguration();
        identityCacheConfigurationHolder = IdentityConfigParser.getInstance().getIdentityCacheConfigurationHolder();
        identityCookiesConfigurationHolder = IdentityConfigParser.getIdentityCookieConfigurationHolder();
        legacyFeatureConfigurationHolder = IdentityConfigParser.getLegacyFeatureConfigurationHolder();
        reverseProxyConfigurationHolder = IdentityConfigParser.getInstance().getReverseProxyConfigurationHolder();
        cookiesToInvalidateConfigurationHolder =
                IdentityConfigParser.getInstance().getCookiesToInvalidateConfigurationHolder();
        storeProcedureBasedDAOConfigurationHolder =
                IdentityConfigParser.getInstance().getStoreProcedureBasedDAOConfigurationHolder();
    }

    public static String getPPIDDisplayValue(String value) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Generating display value of PPID : " + value);
        }
        byte[] rawPpid = Base64.getDecoder().decode(value);

        String algorithm;
        if (Boolean.parseBoolean(IdentityUtil.getProperty(IdentityConstants.IDENTITY_UTIL_ENABLE_SHA256))) {
            algorithm = SHA256_ALGORITHM;
        } else {
            algorithm = SHA1_ALGORITHM;
        }
        MessageDigest sha = MessageDigest.getInstance(algorithm);
        sha.update(rawPpid);
        byte[] hashId = sha.digest();
        char[] returnChars = new char[10];
        for (int i = 0; i < 10; i++) {
            int rawValue = (hashId[i] + 128) % 32;
            returnChars[i] = ppidDisplayCharMap[rawValue];
        }
        StringBuilder sb = new StringBuilder();
        sb.append(returnChars, 0, 3);
        sb.append("-");
        sb.append(returnChars, 3, 4);
        sb.append("-");
        sb.append(returnChars, 6, 3);
        return sb.toString();

    }

    /**
     * Serialize the given node to a String.
     *
     * @param node Node to be serialized.
     * @return The serialized node as a java.lang.String instance.
     */
    public static String nodeToString(Node node) {
        return DOM2Writer.nodeToString(node);
    }

    public static String getHMAC(String secretKey, String baseString) throws SignatureException {
        try {
            String algorithm;
            if (Boolean.parseBoolean(IdentityUtil.getProperty(IdentityConstants.IDENTITY_UTIL_ENABLE_SHA256))) {
                algorithm = HMAC_SHA256_ALGORITHM;
            } else {
                algorithm = HMAC_SHA1_ALGORITHM;
            }
            SecretKeySpec key = new SecretKeySpec(secretKey.getBytes(), algorithm);
            Mac mac = Mac.getInstance(algorithm);
            mac.init(key);
            byte[] rawHmac = mac.doFinal(baseString.getBytes());
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new SignatureException("Failed to generate HMAC : " + e.getMessage(), e);
        }
    }

    /**
     * Generates a secure random hexadecimal string using DRBG PRNG and digest
     *
     * @return Random hexadecimal encoded String
     * @throws Exception
     */
    public static String generateUUID() throws Exception {

        try {
            // DRBG Pseudo Random Number Generator
            SecureRandom prng = SecureRandom.getInstance("DRBG");

            // random number
            String randomNum = Integer.toString(prng.nextInt());
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha.digest(randomNum.getBytes());

            // Hexadecimal encoding
            return new String(Hex.encodeHex(digest));

        } catch (NoSuchAlgorithmException e) {
            throw new Exception("Failed to generate UUID ", e);
        }
    }

    /**
     * Generates a random number using two UUIDs and HMAC-SHA256
     *
     * @return Random Number generated.
     * @throws IdentityException Exception due to Invalid Algorithm or Invalid Key
     */
    public static String getRandomNumber() throws IdentityException {
        try {
            String secretKey = UUIDGenerator.generateUUID();
            String baseString = UUIDGenerator.generateUUID();

            String algorithm;
            if (Boolean.parseBoolean(IdentityUtil.getProperty(IdentityConstants.IDENTITY_UTIL_ENABLE_SHA256))) {
                algorithm = HMAC_SHA256_ALGORITHM;
            } else {
                algorithm = HMAC_SHA1_ALGORITHM;
            }
            SecretKeySpec key = new SecretKeySpec(secretKey.getBytes(), algorithm);
            Mac mac = Mac.getInstance(algorithm);
            mac.init(key);
            byte[] rawHmac = mac.doFinal(baseString.getBytes());
            String random = Base64.getEncoder().encodeToString(rawHmac);
            // Registry doesn't have support for these character.
            random = random.replace("/", "_");
            random = random.replace("=", "a");
            random = random.replace("+", "f");
            return random;
        } catch (Exception e) {
            log.error("Error when generating a random number.", e);
            throw IdentityException.error("Error when generating a random number.", e);
        }
    }

    public static int getRandomInteger() throws IdentityException {

        try {
            SecureRandom prng = SecureRandom.getInstance("DRBG");
            int number = prng.nextInt();
            while (number < 0) {
                number = prng.nextInt();
            }
            return number;
        } catch (NoSuchAlgorithmException e) {
            log.error("Error when generating a random number.", e);
            throw IdentityException.error("Error when generating a random number.", e);
        }

    }

    public static String getIdentityConfigDirPath() {
        return CarbonUtils.getCarbonConfigDirPath() + File.separator + "identity";
    }

    /**
     * Get the uri path for the given endpoint by adding the proxy context path and the web context root if requested.
     *
     * @param endpoint            endpoint path
     * @param addProxyContextPath whether to add the proxy context path to the url
     * @param addWebContextRoot   whether to add the web context root to the url
     * @return url path
     * @throws IdentityRuntimeException
     */
    public static String getEndpointURIPath(String endpoint, boolean addProxyContextPath, boolean addWebContextRoot)
            throws IdentityRuntimeException {

        StringBuilder serverUrl = new StringBuilder();
        appendContextToUri(endpoint, addProxyContextPath, addWebContextRoot, serverUrl);

        return serverUrl.toString();
    }

    /**
     * This method is used to return a URL with a proxy context path, a web context root and the tenant domain (If
     * required) when provided with a URL context.
     *
     * @param endpoint            Endpoint.
     * @param addProxyContextPath Add proxy context path to the URL.
     * @param addWebContextRoot   Add web context path to the URL.
     * @return Complete URL for the given URL context.
     * @throws IdentityRuntimeException If error occurred while constructing the URL
     */
    public static String getServerURL(String endpoint, boolean addProxyContextPath, boolean addWebContextRoot)
            throws IdentityRuntimeException {

        String hostName = ServerConfiguration.getInstance().getFirstProperty(IdentityCoreConstants.HOST_NAME);

        try {
            if (hostName == null) {
                hostName = NetworkUtils.getLocalHostname();
            }
        } catch (SocketException e) {
            throw IdentityRuntimeException.error("Error while trying to read hostname.", e);
        }

        StringBuilder serverUrl = getServerUrlWithPort(hostName);

        appendContextToUri(endpoint, addProxyContextPath, addWebContextRoot, serverUrl);
        return serverUrl.toString();
    }

    /**
     * Returns the Management console URL for the endpoint with the proxy context and the web context root.
     *
     * @param endpoint            Endpoint that needs to be called.
     * @param addProxyContextPath Flag that defines to add the proxy context path.
     * @param addWebContextRoot   Flag that defines to add the web context root.
     * @return Full path for the endpoint.
     * @throws IdentityRuntimeException When an exception is occurred.
     */
    public static String getMgtConsoleURL(String endpoint, boolean addProxyContextPath, boolean addWebContextRoot)
            throws IdentityRuntimeException {

        String hostName = ServerConfiguration.getInstance().getFirstProperty(
                IdentityCoreConstants.MGT_CONSOLE_HOST_NAME);

        if (StringUtils.isBlank(hostName)) {
            hostName = NetworkUtils.getMgtHostName();
        }

        StringBuilder serverUrl = getServerUrlWithPort(hostName);

        appendContextToUri(endpoint, addProxyContextPath, addWebContextRoot, serverUrl);
        return serverUrl.toString();
    }

    /**
     * Checks whether the second domain is a subdomain of the first domain.
     *
     * @param domainName    Domain name.
     * @param subdomainName Subdomain name.
     * @return true if the second domain is a subdomain of the first domain.
     */
    public static boolean isSubdomain(String domainName, String subdomainName) {

        if (StringUtils.isBlank(domainName) || StringUtils.isBlank(subdomainName)) {
            return false;
        }
        subdomainName = subdomainName.toLowerCase();
        domainName = domainName.toLowerCase();

        if (subdomainName.equals(domainName)) {
            return true;
        }
        return subdomainName.endsWith("." + domainName);
    }

    /**
     * Get the root domain of the given domain.
     * Note: this assumes that the root domain only consists of two parts (eg: wso2.io). The method will not work for
     * TLDs with more than two parts (eg: wso2.co.uk).
     *
     * @param domain Domain.
     * @return Root domain.
     */
    public static String getRootDomain(String domain) {

        if (StringUtils.isBlank(domain)) {
            return domain;
        }
        String[] domainParts = domain.split("\\.");
        int length = domainParts.length;

        if (length > 2) {
            return domainParts[length - 2] + "." + domainParts[length - 1];
        } else {
            return domain;
        }
    }

    private static StringBuilder getServerUrlWithPort(String hostName) {

        String mgtTransport = CarbonUtils.getManagementTransport();
        AxisConfiguration axisConfiguration = IdentityCoreServiceComponent.getConfigurationContextService().
                getServerConfigContext().getAxisConfiguration();
        int mgtTransportPort = CarbonUtils.getTransportProxyPort(axisConfiguration, mgtTransport);
        if (mgtTransportPort <= 0) {
            mgtTransportPort = CarbonUtils.getTransportPort(axisConfiguration, mgtTransport);
        }
        if (hostName.endsWith("/")) {
            hostName = hostName.substring(0, hostName.length() - 1);
        }
        StringBuilder serverUrl = new StringBuilder(mgtTransport).append("://").append(hostName.toLowerCase());
        // If it's well known HTTPS port, skip adding port
        if (mgtTransportPort != IdentityCoreConstants.DEFAULT_HTTPS_PORT) {
            serverUrl.append(":").append(mgtTransportPort);
        }
        return serverUrl;
    }

    private static void appendContextToUri(String endpoint, boolean addProxyContextPath, boolean addWebContextRoot,
                                           StringBuilder serverUrl) {

        // If ProxyContextPath is defined then append it
        if (addProxyContextPath) {
            // If ProxyContextPath is defined then append it
            String proxyContextPath = ServerConfiguration.getInstance().getFirstProperty(IdentityCoreConstants
                    .PROXY_CONTEXT_PATH);
            if (StringUtils.isNotBlank(proxyContextPath)) {
                if (proxyContextPath.trim().charAt(0) != '/') {
                    serverUrl.append("/").append(proxyContextPath.trim());
                } else {
                    serverUrl.append(proxyContextPath.trim());
                }
            }
        }

        // If webContextRoot is defined then append it
        if (addWebContextRoot) {
            String webContextRoot = ServerConfiguration.getInstance().getFirstProperty(IdentityCoreConstants
                    .WEB_CONTEXT_ROOT);
            if (StringUtils.isNotBlank(webContextRoot)) {
                if (webContextRoot.trim().charAt(0) != '/') {
                    serverUrl.append("/").append(webContextRoot.trim());
                } else {
                    serverUrl.append(webContextRoot.trim());
                }
            }
        }
        if (StringUtils.isNotBlank(endpoint)) {
            if (!serverUrl.toString().endsWith("/") && endpoint.trim().charAt(0) != '/') {
                serverUrl.append("/").append(endpoint.trim());
            } else if (serverUrl.toString().endsWith("/") && endpoint.trim().charAt(0) == '/') {
                serverUrl.append(endpoint.trim().substring(1));
            } else {
                serverUrl.append(endpoint.trim());
            }
        }
        if (serverUrl.toString().endsWith("/")) {
            serverUrl.setLength(serverUrl.length() - 1);
        }
    }

    /**
     * Get the axis service path
     *
     * @return String
     */
    public static String getServicePath() {
        return IdentityCoreServiceComponent.getConfigurationContextService().getServerConfigContext().getServicePath();
    }

    /**
     * Create DocumentBuilderFactory with the XXE and XEE prevention measurements.
     *
     * @return DocumentBuilderFactory instance
     */
    public static DocumentBuilderFactory getSecuredDocumentBuilderFactory() {

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        try {
            dbf.setFeature(Constants.SAX_FEATURE_PREFIX + Constants.EXTERNAL_GENERAL_ENTITIES_FEATURE, false);
            dbf.setFeature(Constants.SAX_FEATURE_PREFIX + Constants.EXTERNAL_PARAMETER_ENTITIES_FEATURE, false);
            dbf.setFeature(Constants.XERCES_FEATURE_PREFIX + Constants.LOAD_EXTERNAL_DTD_FEATURE, false);
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

        } catch (ParserConfigurationException e) {
            log.error("Failed to load XML Processor Feature " + Constants.EXTERNAL_GENERAL_ENTITIES_FEATURE + " or " +
                    Constants.EXTERNAL_PARAMETER_ENTITIES_FEATURE + " or " + Constants.LOAD_EXTERNAL_DTD_FEATURE +
                    " or secure-processing.");
        }

        SecurityManager securityManager = new SecurityManager();
        securityManager.setEntityExpansionLimit(ENTITY_EXPANSION_LIMIT);
        dbf.setAttribute(Constants.XERCES_PROPERTY_PREFIX + Constants.SECURITY_MANAGER_PROPERTY, securityManager);

        return dbf;

    }

    /**
     * Create TransformerFactory with the XXE and XEE prevention measurements.
     *
     * @return TransformerFactory instance
     */
    public static TransformerFactory getSecuredTransformerFactory() {

        TransformerFactory trfactory;
        try {
            // Prevent XXE Attack by ensure using the correct factory class to create TrasformerFactory instance.
            // This will instruct Java to use to version which supports using ACCESS_EXTERNAL_DTD argument.
            trfactory = TransformerFactory.newInstance(JAVAX_TRANSFORMER_PROP_VAL, null);
        } catch (TransformerFactoryConfigurationError e) {
            log.error("Failed to load default TransformerFactory", e);
            // This part uses the default implementation of xalan.
            trfactory = TransformerFactory.newInstance();
        }

        try {
            trfactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (TransformerConfigurationException e) {
            log.error("Failed to load XML Processor Feature " + XMLConstants.FEATURE_SECURE_PROCESSING +
                    " for secure-processing.");
        }
        trfactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        trfactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        return trfactory;
    }

    /**
     * Check the case sensitivity of the user store in which the user is in.
     *
     * @param username Full qualified username
     * @return
     */
    public static boolean isUserStoreInUsernameCaseSensitive(String username) {

        boolean isUsernameCaseSensitive = true;
        try {
            String tenantDomain = MultitenantUtils.getTenantDomain(username);
            int tenantId = IdentityTenantUtil.getRealmService().getTenantManager().getTenantId(tenantDomain);
            return isUserStoreInUsernameCaseSensitive(username, tenantId);
        } catch (UserStoreException e) {
            if (log.isDebugEnabled()) {
                log.debug("Error while reading user store property CaseInsensitiveUsername. Considering as case " +
                        "sensitive.");
            }
        }
        return isUsernameCaseSensitive;
    }

    /**
     * Check the case sensitivity of the user store in which the user is in.
     *
     * @param username user name with user store domain
     * @param tenantId tenant id of the user
     * @return
     */
    public static boolean isUserStoreInUsernameCaseSensitive(String username, int tenantId) {

        return isUserStoreCaseSensitive(IdentityUtil.extractDomainFromName(username), tenantId);
    }

    /**
     * Check the case sensitivity of the user store.
     *
     * @param userStoreDomain user store domain
     * @param tenantId        tenant id of the user store
     * @return
     */
    public static boolean isUserStoreCaseSensitive(String userStoreDomain, int tenantId) {

        boolean isUsernameCaseSensitive = true;
        if (tenantId == MultitenantConstants.INVALID_TENANT_ID) {
            //this is to handle federated scenarios
            return true;
        }
        try {
            UserRealm tenantUserRealm = IdentityTenantUtil.getRealmService().getTenantUserRealm(tenantId);
            if (tenantUserRealm != null) {
                org.wso2.carbon.user.core.UserStoreManager userStoreManager = (org.wso2.carbon.user.core.UserStoreManager) tenantUserRealm
                        .getUserStoreManager();
                org.wso2.carbon.user.core.UserStoreManager userAvailableUserStoreManager = userStoreManager.getSecondaryUserStoreManager(userStoreDomain);
                return isUserStoreCaseSensitive(userAvailableUserStoreManager);
            }
        } catch (UserStoreException e) {
            if (log.isDebugEnabled()) {
                log.debug("Error while reading user store property CaseInsensitiveUsername. Considering as case " +
                        "sensitive.");
            }
        }
        return isUsernameCaseSensitive;
    }

    /**
     * Check the case sensitivity of the user store.
     *
     * @param userStoreManager
     * @return
     */
    public static boolean isUserStoreCaseSensitive(UserStoreManager userStoreManager) {

        if (userStoreManager == null) {
            //this is done to handle federated scenarios. For federated scenarios, there is no user store manager for
            // the user
            return true;
        }
        String caseInsensitiveUsername = userStoreManager.getRealmConfiguration()
                .getUserStoreProperty(IdentityCoreConstants.CASE_INSENSITIVE_USERNAME);
        if (caseInsensitiveUsername == null && log.isDebugEnabled()) {
            log.debug("Error while reading user store property CaseInsensitiveUsername. Considering as case sensitive" +
                    ".");
        }
        return !Boolean.parseBoolean(caseInsensitiveUsername);
    }

    /**
     * This returns whether case sensitive user name can be used as the cache key.
     *
     * @param userStoreManager user-store manager
     * @return true if case sensitive username can be use as cache key
     */
    public static boolean isUseCaseSensitiveUsernameForCacheKeys(UserStoreManager userStoreManager) {

        if (userStoreManager == null) {
            //this is done to handle federated scenarios. For federated scenarios, there is no user store manager for
            // the user
            return true;
        }
        String useCaseSensitiveUsernameForCacheKeys = userStoreManager.getRealmConfiguration()
                .getUserStoreProperty(IdentityCoreConstants.USE_CASE_SENSITIVE_USERNAME_FOR_CACHE_KEYS);
        if (StringUtils.isBlank(useCaseSensitiveUsernameForCacheKeys)) {
            if (log.isDebugEnabled()) {
                log.debug("Failed to read user store property UseCaseSensitiveUsernameForCacheKeys. Considering as "
                        + "case sensitive.");
            }
            return true;
        }
        return Boolean.parseBoolean(useCaseSensitiveUsernameForCacheKeys);
    }

    public static boolean isNotBlank(String input) {
        if (StringUtils.isNotBlank(input) && !"null".equals(input.trim())) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean isBlank(String input) {
        if (StringUtils.isBlank(input) || "null".equals(input.trim())) {
            return true;
        } else {
            return false;
        }
    }

    public static long getCleanUpTimeout() {

        String cleanUpTimeout = IdentityUtil.getProperty(IdentityConstants.ServerConfig.CLEAN_UP_TIMEOUT);
        if (StringUtils.isBlank(cleanUpTimeout)) {
            cleanUpTimeout = IdentityConstants.ServerConfig.CLEAN_UP_TIMEOUT_DEFAULT;
        } else if (!StringUtils.isNumeric(cleanUpTimeout)) {
            cleanUpTimeout = IdentityConstants.ServerConfig.CLEAN_UP_TIMEOUT_DEFAULT;
        }
        return Long.parseLong(cleanUpTimeout);
    }

    public static long getCleanUpPeriod(String tenantDomain) {

        String cleanUpPeriod = IdentityUtil.getProperty(IdentityConstants.ServerConfig.CLEAN_UP_PERIOD);
        if (StringUtils.isBlank(cleanUpPeriod)) {
            cleanUpPeriod = IdentityConstants.ServerConfig.CLEAN_UP_PERIOD_DEFAULT;
        } else if (!StringUtils.isNumeric(cleanUpPeriod)) {
            cleanUpPeriod = IdentityConstants.ServerConfig.CLEAN_UP_PERIOD_DEFAULT;
        }
        return Long.parseLong(cleanUpPeriod);
    }

    public static long getOperationCleanUpTimeout() {

        String cleanUpTimeout = IdentityUtil.getProperty(IdentityConstants.ServerConfig.OPERATION_CLEAN_UP_TIMEOUT);
        if (StringUtils.isBlank(cleanUpTimeout)) {
            cleanUpTimeout = IdentityConstants.ServerConfig.OPERATION_CLEAN_UP_TIMEOUT_DEFAULT;
        } else if (!StringUtils.isNumeric(cleanUpTimeout)) {
            cleanUpTimeout = IdentityConstants.ServerConfig.OPERATION_CLEAN_UP_TIMEOUT_DEFAULT;
        }
        return Long.parseLong(cleanUpTimeout);
    }

    public static long getTempDataCleanUpTimeout() {

        String cleanUpTimeout = IdentityUtil.getProperty(IdentityConstants.ServerConfig.TEMP_DATA_CLEAN_UP_TIMEOUT);
        if (StringUtils.isBlank(cleanUpTimeout)) {
            cleanUpTimeout = IdentityConstants.ServerConfig.TEMP_DATA_CLEAN_UP_TIMEOUT_DEFAULT;
        } else if (!StringUtils.isNumeric(cleanUpTimeout)) {
            cleanUpTimeout = IdentityConstants.ServerConfig.TEMP_DATA_CLEAN_UP_TIMEOUT_DEFAULT;
        }
        return Long.parseLong(cleanUpTimeout);
    }

    public static long getOperationCleanUpPeriod(String tenantDomain) {

        String cleanUpPeriod = IdentityUtil.getProperty(IdentityConstants.ServerConfig.OPERATION_CLEAN_UP_PERIOD);
        if (StringUtils.isBlank(cleanUpPeriod)) {
            cleanUpPeriod = IdentityConstants.ServerConfig.OPERATION_CLEAN_UP_PERIOD_DEFAULT;
        } else if (!StringUtils.isNumeric(cleanUpPeriod)) {
            cleanUpPeriod = IdentityConstants.ServerConfig.OPERATION_CLEAN_UP_PERIOD_DEFAULT;
        }
        return Long.parseLong(cleanUpPeriod);
    }

    public static String extractDomainFromName(String nameWithDomain) {

        if (nameWithDomain.indexOf(UserCoreConstants.DOMAIN_SEPARATOR) > 0) {
            String domain = nameWithDomain.substring(0, nameWithDomain.indexOf(UserCoreConstants.DOMAIN_SEPARATOR));
            if (UserCoreConstants.INTERNAL_DOMAIN.equalsIgnoreCase(domain) || WORKFLOW_DOMAIN.equalsIgnoreCase(domain)
                    || APPLICATION_DOMAIN.equalsIgnoreCase(domain)) {
                return domain.substring(0, 1).toUpperCase() + domain.substring(1).toLowerCase();
            }
            return domain.toUpperCase();
        } else {
            return getPrimaryDomainName();
        }
    }

    /**
     * Appends domain name to the user/role name
     *
     * @param name       user/role name
     * @param domainName domain name
     * @return application name with domain name
     */
    public static String addDomainToName(String name, String domainName) {

        if (domainName != null && name != null && !name.contains(UserCoreConstants.DOMAIN_SEPARATOR)) {
            if (!UserCoreConstants.PRIMARY_DEFAULT_DOMAIN_NAME.equalsIgnoreCase(domainName)) {
                if (UserCoreConstants.INTERNAL_DOMAIN.equalsIgnoreCase(domainName) ||
                        WORKFLOW_DOMAIN.equalsIgnoreCase(domainName) || APPLICATION_DOMAIN.equalsIgnoreCase(domainName)) {
                    name = domainName.substring(0, 1).toUpperCase() + domainName.substring(1).toLowerCase() +
                            UserCoreConstants.DOMAIN_SEPARATOR + name;
                } else {
                    name = domainName.toUpperCase() + UserCoreConstants.DOMAIN_SEPARATOR + name;
                }
            }
        }
        return name;
    }

    public static String getPrimaryDomainName() {
        RealmConfiguration realmConfiguration = IdentityTenantUtil.getRealmService().getBootstrapRealmConfiguration();
        if (realmConfiguration.getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME) != null) {
            return realmConfiguration.getUserStoreProperty(
                    UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME).toUpperCase();
        } else {
            return UserCoreConstants.PRIMARY_DEFAULT_DOMAIN_NAME;
        }
    }

    public static boolean isValidFileName(String fileName) {
        String fileNameRegEx = ServerConfiguration.getInstance().getFirstProperty(IdentityCoreConstants.FILE_NAME_REGEX);

        if (isBlank(fileNameRegEx)) {
            fileNameRegEx = DEFAULT_FILE_NAME_REGEX;
        }

        Pattern pattern = Pattern.compile(fileNameRegEx, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE |
                Pattern.COMMENTS);
        Matcher matcher = pattern.matcher(fileName);
        return matcher.matches();
    }

    /**
     * Replaces the port number placeholder with the actual port number for non-standard ports, and
     * if the ports are standard ports, namely 443 for https and 80 for http, the port number is dropped.
     *
     * @param urlWithPlaceholders URL with the placeholders.
     * @return URL with the port number placeholder replaced.
     */
    public static String replacePortNumberPlaceholder(String urlWithPlaceholders, boolean dropStandardPort) {

        if (StringUtils.contains(urlWithPlaceholders, IdentityConstants.CarbonPlaceholders.CARBON_PORT)) {

            String mgtTransport = CarbonUtils.getManagementTransport();
            AxisConfiguration axisConfiguration = IdentityCoreServiceComponent.getConfigurationContextService().
                    getServerConfigContext().getAxisConfiguration();

            int mgtTransportProxyPort = CarbonUtils.getTransportProxyPort(axisConfiguration, mgtTransport);
            String mgtTransportPort = Integer.toString(mgtTransportProxyPort);

            if (mgtTransportProxyPort <= 0) {
                if (StringUtils.equals(mgtTransport, HTTP)) {
                    mgtTransportPort = System.getProperty(
                            IdentityConstants.CarbonPlaceholders.CARBON_PORT_HTTP_PROPERTY);
                } else {
                    mgtTransportPort = System.getProperty(
                            IdentityConstants.CarbonPlaceholders.CARBON_PORT_HTTPS_PROPERTY);
                }
            }

            if (dropStandardPort && ((StringUtils.equals(mgtTransport, HTTP) &&
                    StringUtils.equals(mgtTransportPort, String.valueOf(HttpURL.DEFAULT_PORT))) ||
                    (StringUtils.equals(mgtTransport, HTTPS) &&
                            StringUtils.equals(mgtTransportPort, String.valueOf(HttpsURL.DEFAULT_PORT))))) {
                urlWithPlaceholders = StringUtils.replace(urlWithPlaceholders, ":" +
                        IdentityConstants.CarbonPlaceholders.CARBON_PORT, StringUtils.EMPTY);
            } else {
                urlWithPlaceholders = StringUtils.replace(urlWithPlaceholders,
                        IdentityConstants.CarbonPlaceholders.CARBON_PORT, mgtTransportPort);
            }
        }
        return urlWithPlaceholders;
    }

    /**
     * Replace the placeholders with the related values in the URL.
     *
     * @param urlWithPlaceholders URL with the placeholders.
     * @return URL filled with the placeholder values.
     */
    public static String fillURLPlaceholders(String urlWithPlaceholders) {

        if (StringUtils.isBlank(urlWithPlaceholders)) {
            return urlWithPlaceholders;
        }

        // First replace carbon placeholders and then move on to identity related placeholders.
        urlWithPlaceholders = Utils.replaceSystemProperty(urlWithPlaceholders);

        if (StringUtils.contains(urlWithPlaceholders, IdentityConstants.CarbonPlaceholders.CARBON_HOST)) {

            String hostName = ServerConfiguration.getInstance().getFirstProperty(IdentityCoreConstants.HOST_NAME);

            if (hostName == null) {
                try {
                    hostName = NetworkUtils.getLocalHostname();
                } catch (SocketException e) {
                    throw IdentityRuntimeException.error("Error while trying to read hostname.", e);
                }
            }

            urlWithPlaceholders = StringUtils.replace(urlWithPlaceholders,
                    IdentityConstants.CarbonPlaceholders.CARBON_HOST,
                    hostName);
        }

        urlWithPlaceholders = replacePortNumberPlaceholder(urlWithPlaceholders, Boolean.FALSE);

        if (StringUtils.contains(urlWithPlaceholders, IdentityConstants.CarbonPlaceholders.CARBON_PORT_HTTP)) {

            String httpPort = System.getProperty(IdentityConstants.CarbonPlaceholders.CARBON_PORT_HTTP_PROPERTY);
            urlWithPlaceholders = StringUtils.replace(urlWithPlaceholders,
                    IdentityConstants.CarbonPlaceholders.CARBON_PORT_HTTP,
                    httpPort);
        }

        if (StringUtils.contains(urlWithPlaceholders, IdentityConstants.CarbonPlaceholders.CARBON_PORT_HTTPS)) {

            String httpsPort = System.getProperty(IdentityConstants.CarbonPlaceholders.CARBON_PORT_HTTPS_PROPERTY);
            urlWithPlaceholders = StringUtils.replace(urlWithPlaceholders,
                    IdentityConstants.CarbonPlaceholders.CARBON_PORT_HTTPS,
                    httpsPort);
        }

        if (StringUtils.contains(urlWithPlaceholders, IdentityConstants.CarbonPlaceholders.CARBON_PROTOCOL)) {

            String mgtTransport = CarbonUtils.getManagementTransport();
            urlWithPlaceholders = StringUtils.replace(urlWithPlaceholders,
                    IdentityConstants.CarbonPlaceholders.CARBON_PROTOCOL,
                    mgtTransport);
        }

        if (StringUtils.contains(urlWithPlaceholders, IdentityConstants.CarbonPlaceholders.CARBON_PROXY_CONTEXT_PATH)) {

            String proxyContextPath = ServerConfiguration.getInstance().getFirstProperty(IdentityCoreConstants
                    .PROXY_CONTEXT_PATH);
            urlWithPlaceholders = StringUtils.replace(urlWithPlaceholders,
                    IdentityConstants.CarbonPlaceholders.CARBON_PROXY_CONTEXT_PATH,
                    proxyContextPath);
        }

        if (StringUtils.contains(urlWithPlaceholders, IdentityConstants.CarbonPlaceholders.CARBON_WEB_CONTEXT_ROOT)) {

            String webContextRoot = ServerConfiguration.getInstance().getFirstProperty(IdentityCoreConstants
                    .WEB_CONTEXT_ROOT);
            urlWithPlaceholders = StringUtils.replace(urlWithPlaceholders,
                    IdentityConstants.CarbonPlaceholders.CARBON_WEB_CONTEXT_ROOT,
                    webContextRoot);
        }

        if (StringUtils.contains(urlWithPlaceholders, CarbonConstants.CARBON_HOME_PARAMETER)) {

            String carbonHome = CarbonUtils.getCarbonHome();
            urlWithPlaceholders = StringUtils.replace(urlWithPlaceholders,
                    CarbonConstants.CARBON_HOME_PARAMETER,
                    carbonHome);
        }

        if (StringUtils.contains(urlWithPlaceholders, IdentityConstants.CarbonPlaceholders.CARBON_CONTEXT)) {

            String carbonContext = ServerConfiguration.getInstance().getFirstProperty(IdentityCoreConstants
                    .WEB_CONTEXT_ROOT);

            if (carbonContext.equals("/")) {
                carbonContext = "";
            }

            urlWithPlaceholders = StringUtils.replace(urlWithPlaceholders,
                    IdentityConstants.CarbonPlaceholders.CARBON_CONTEXT,
                    carbonContext);
        }

        return urlWithPlaceholders;
    }

    /**
     * Check whether the given token value is appropriate to log.
     *
     * @param tokenName Name of the token.
     * @return True if token is appropriate to log.
     */
    public static boolean isTokenLoggable(String tokenName) {

        IdentityLogTokenParser identityLogTokenParser = IdentityLogTokenParser.getInstance();
        Map<String, String> logTokenMap = identityLogTokenParser.getLogTokenMap();

        return Boolean.valueOf(logTokenMap.get(tokenName));
    }

    /**
     * Get the host name of the server.
     *
     * @return Hostname
     */
    public static String getHostName() {

        String hostName = ServerConfiguration.getInstance().getFirstProperty(IdentityCoreConstants.HOST_NAME);
        if (hostName == null) {
            try {
                hostName = NetworkUtils.getLocalHostname();
            } catch (SocketException e) {
                throw IdentityRuntimeException.error("Error while trying to read hostname.", e);
            }
        }
        return hostName;
    }

    public static String buildQueryString(Map<String, String[]> parameterMap) throws UnsupportedEncodingException {

        return "?" + buildQueryComponent(parameterMap);
    }

    public static String buildFragmentString(Map<String, String[]> parameterMap) throws UnsupportedEncodingException {

        return "#" + buildQueryComponent(parameterMap);
    }

    public static String buildQueryUrl(String baseUrl, Map<String, String[]> parameterMap) throws
            UnsupportedEncodingException {


        if (StringUtils.isBlank(baseUrl)) {
            throw IdentityRuntimeException.error("Base URL is blank: " + baseUrl);
        } else if (baseUrl.contains("#")) {
            throw IdentityRuntimeException.error("Query URL cannot contain \'#\': " + baseUrl);
        }
        StringBuilder queryString = new StringBuilder(baseUrl);

        if (parameterMap != null && parameterMap.size() > 0) {
            if(queryString.indexOf("?") < 0) {
                queryString.append("?");
            } else {
                queryString.append("&");
            }
            queryString.append(buildQueryComponent(parameterMap));
        }

        return queryString.toString();
    }

    public static String buildFragmentUrl(String baseUrl, Map<String, String[]> parameterMap) throws
            UnsupportedEncodingException {


        if (StringUtils.isBlank(baseUrl)) {
            throw IdentityRuntimeException.error("Base URL is blank: " + baseUrl);
        } else if (baseUrl.contains("?")) {
            throw IdentityRuntimeException.error("Fragment URL cannot contain \'?\': " + baseUrl);
        }
        StringBuilder queryString = new StringBuilder(baseUrl);
        if (queryString.indexOf("#") < 0) {
            queryString.append("#");
        }
        queryString.append(buildQueryComponent(parameterMap));
        return queryString.toString();
    }

    public static String buildQueryComponent(Map<String, String[]> parameterMap) throws UnsupportedEncodingException {
        if (MapUtils.isEmpty(parameterMap)) {
            return StringUtils.EMPTY;
        }
        StringBuilder queryString = new StringBuilder("");
        boolean isFirst = true;
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            //param value may be empty, hence empty check is avoided.
            if (StringUtils.isBlank(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            for (String paramValue : entry.getValue()) {
                if (paramValue == null) {
                    continue;
                }
                if (isFirst) {
                    isFirst = false;
                } else {
                    queryString.append("&");
                }
                queryString.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name()));
                queryString.append("=");
                queryString.append(URLEncoder.encode(paramValue, StandardCharsets.UTF_8.name()));

            }
        }
        return queryString.toString();
    }

    /**
     * Get client IP address from the http request
     *
     * @param request http servlet request
     * @return IP address of the initial client
     */
    public static String getClientIpAddress(HttpServletRequest request) {
        for (String header : IdentityConstants.HEADERS_WITH_IP) {
            String ip = request.getHeader(header);
            if (ip != null && ip.length() != 0 && !IdentityConstants.UNKNOWN.equalsIgnoreCase(ip)) {
                return getFirstIP(ip);
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * Get the first IP from a comma separated list of IPs
     *
     * @param commaSeparatedIPs String which contains comma+space separated IPs
     * @return First IP
     */
    public static String getFirstIP(String commaSeparatedIPs) {
        if (StringUtils.isNotEmpty(commaSeparatedIPs) && commaSeparatedIPs.contains(",")) {
            return commaSeparatedIPs.split(",")[0];
        }
        return commaSeparatedIPs;
    }

    /**
     * Get the server synchronization tolerance value in seconds
     *
     * @return clock skew in seconds
     */
    public static int getClockSkewInSeconds() {

        String clockSkewConfigValue = IdentityUtil.getProperty(IdentityConstants.ServerConfig.CLOCK_SKEW);
        if (StringUtils.isBlank(clockSkewConfigValue) || !StringUtils.isNumeric(clockSkewConfigValue)) {
            clockSkewConfigValue = IdentityConstants.ServerConfig.CLOCK_SKEW_DEFAULT;
        }
        return Integer.parseInt(clockSkewConfigValue);
    }

    /**
     * Get the server config for enabling federated user association
     *
     * @return isFederatedUserAssociationEnabled value
     */
    public static boolean isFederatedUserAssociationEnabled() {

        String enableFedUserAssocicationConfigValue = IdentityUtil.getProperty(
                IdentityConstants.ServerConfig.ENABLE_FEDERATED_USER_ASSOCIATION);
        if (StringUtils.isBlank(enableFedUserAssocicationConfigValue)) {
            enableFedUserAssocicationConfigValue =
                    IdentityConstants.ServerConfig.ENABLE_FEDERATED_USER_ASSOCIATION_DEFAULT;
        }

        return Boolean.parseBoolean(enableFedUserAssocicationConfigValue);
    }

    /**
     * Returns whether the passed operation is supported by userstore or not
     *
     * @param userStoreManager User Store
     * @param operation        Operation name
     * @return true if the operation is supported by userstore. False if it doesnt
     */
    public static boolean isSupportedByUserStore(UserStoreManager userStoreManager, String operation) {
        boolean isOperationSupported = true;
        if (userStoreManager != null) {
            String isOperationSupportedProperty = userStoreManager.getRealmConfiguration().getUserStoreProperty
                    (operation);
            if (StringUtils.isNotBlank(isOperationSupportedProperty)) {
                isOperationSupported = Boolean.parseBoolean(isOperationSupportedProperty);
            }
        }
        return isOperationSupported;
    }

    /**
     * Returns whether the recovery endpoint is available or not.
     *
     * @return true if the EnableRecoveryEndpoint. False if it doesnt
     */
    public static boolean isRecoveryEPAvailable() {

        String enableRecoveryEPUrlProperty = getProperty(ENABLE_RECOVERY_ENDPOINT);
        return Boolean.parseBoolean(enableRecoveryEPUrlProperty);
    }

    /**
     * Returns whether the self signup endpoint is available or not.
     *
     * @return true if the EnableSelfSignUpEndpoint. False if it doesnt
     */
    public static boolean isSelfSignUpEPAvailable() {

        String enableSelfSignEPUpUrlProperty = getProperty(ENABLE_SELF_SIGN_UP_ENDPOINT);
        return Boolean.parseBoolean(enableSelfSignEPUpUrlProperty);
    }

    /**
     * Returns whether the email based username is enabled or not.
     *
     * @return true if the EnableEmailUserName. False if it doesnt
     */
    public static boolean isEmailUsernameEnabled() {

        String enableEmailUsernameProperty = ServerConfiguration.getInstance().getFirstProperty(ENABLE_EMAIL_USERNAME);
        return Boolean.parseBoolean(enableEmailUsernameProperty);
    }

    /**
     * Returns whether the email based username validation is disabled or not.
     *
     * @return true if the email username validation is disabled. False if it is not.
     */
    public static boolean isEmailUsernameValidationDisabled() {

        String disableEmailUsernameValidationProperty = ServerConfiguration.getInstance()
                .getFirstProperty(DISABLE_EMAIL_USERNAME_VALIDATION);
        return Boolean.parseBoolean(disableEmailUsernameValidationProperty);
    }

    /**
     *
     * Converts and returns a {@link Certificate} object for given PEM content.
     *
     * @param certificateContent
     * @return
     * @throws CertificateException
     */
    public static Certificate convertPEMEncodedContentToCertificate(String certificateContent) throws CertificateException {

        certificateContent = getCertificateString(certificateContent);
        byte[] bytes = org.apache.axiom.om.util.Base64.decode(certificateContent);
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        X509Certificate certificate = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(bytes));
        return certificate;
    }

    /**
     * Extract certificate content and returns a {@link String} object for given PEM content.
     *
     * @param certificateContent initial certificate content
     * @return certificate content without the beginning and end tags
     */
    public static String getCertificateString(String certificateContent) {

        String certificateContentString = null;
        if (certificateContent != null) {
            String certificateContentWithoutPemBegin = certificateContent.startsWith(PEM_BEGIN_CERTFICATE) ?
                    certificateContent.substring(
                            certificateContent.indexOf(PEM_BEGIN_CERTFICATE) + PEM_BEGIN_CERTFICATE.length()) :
                    certificateContent;
            certificateContentString = certificateContentWithoutPemBegin.endsWith(PEM_END_CERTIFICATE) ?
                    certificateContentWithoutPemBegin
                            .substring(0, certificateContentWithoutPemBegin.indexOf(PEM_END_CERTIFICATE)) :
                    certificateContentWithoutPemBegin;
        }

        return certificateContentString;
    }

    /**
     *
     * Returns the PEM encoded certificate out of the given certificate object.
     *
     * @param certificate
     * @return PEM encoded certificate as a {@link String}
     * @throws CertificateException
     */
    public static String convertCertificateToPEM(Certificate certificate) throws CertificateException {

        byte[] encodedCertificate = org.apache.commons.codec.binary.Base64.encodeBase64(certificate.getEncoded());

        String encodedPEM = String.format("%s\n%s\n%s", PEM_BEGIN_CERTFICATE, new String(encodedCertificate),
                PEM_END_CERTIFICATE);

        return encodedPEM;
    }

    /**
     * Checks whether the PEM content is valid.
     *
     * For now only checks whether the certificate is not malformed.
     *
     * @param certificateContent PEM content to be validated.
     * @return true if the content is not malformed, false otherwise.
     */
    public static boolean isValidPEMCertificate(String certificateContent) {

        // Empty content is a valid input since it means no certificate. We only validate if the content is there.
        if (StringUtils.isBlank(certificateContent)) {
            return true;
        }

        try {
            convertPEMEncodedContentToCertificate(certificateContent);
            return true;
        } catch (CertificateException e) {
            return false;
        }
    }

    /**
     * Encodes the given bytes as a base58 string (no checksum is appended).
     *
     * @param input the bytes to encode
     * @return the base58-encoded string
     */
    public static String base58Encode(byte[] input) {

        if (input.length == 0) {
            return "";
        }
        // Count leading zeros.
        int zeros = 0;
        while (zeros < input.length && input[zeros] == 0) {
            ++zeros;
        }
        // Convert base-256 digits to base-58 digits (plus conversion to ASCII characters)
        input = Arrays.copyOf(input, input.length); // since we modify it in-place
        char[] encoded = new char[input.length * 2]; // upper bound
        int outputStart = encoded.length;
        for (int inputStart = zeros; inputStart < input.length; ) {
            encoded[--outputStart] = ALPHABET[divmod(input, inputStart, 256, 58)];
            if (input[inputStart] == 0) {
                ++inputStart; // optimization - skip leading zeros
            }
        }
        // Preserve exactly as many leading encoded zeros in output as there were leading zeros in input.
        while (outputStart < encoded.length && encoded[outputStart] == ENCODED_ZERO) {
            ++outputStart;
        }
        while (--zeros >= 0) {
            encoded[--outputStart] = ENCODED_ZERO;
        }
        // Return encoded string (including encoded leading zeros).
        return new String(encoded, outputStart, encoded.length - outputStart);
    }

    /**
     * Decodes the given base58 string into the original data bytes.
     *
     * @param input the base58-encoded string to decode
     * @return the decoded data bytes
     * @throws RuntimeException if the given string is not a valid base58 string
     */
    public static byte[] base58Decode(String input) throws RuntimeException {

        if (input.length() == 0) {
            return new byte[0];
        }
        // Convert the base58-encoded ASCII chars to a base58 byte sequence (base58 digits).
        byte[] input58 = new byte[input.length()];
        for (int i = 0; i < input.length(); ++i) {
            char c = input.charAt(i);
            int digit = c < 128 ? INDEXES[c] : -1;
            if (digit < 0) {
                throw new RuntimeException(String.format("Invalid character %s at %s", c, i));
            }
            input58[i] = (byte) digit;
        }
        // Count leading zeros.
        int zeros = 0;
        while (zeros < input58.length && input58[zeros] == 0) {
            ++zeros;
        }
        // Convert base-58 digits to base-256 digits.
        byte[] decoded = new byte[input.length()];
        int outputStart = decoded.length;
        for (int inputStart = zeros; inputStart < input58.length; ) {
            decoded[--outputStart] = divmod(input58, inputStart, 58, 256);
            if (input58[inputStart] == 0) {
                ++inputStart; // optimization - skip leading zeros
            }
        }
        // Ignore extra leading zeroes that were added during the calculation.
        while (outputStart < decoded.length && decoded[outputStart] == 0) {
            ++outputStart;
        }
        // Return decoded data (including original number of leading zeros).
        return Arrays.copyOfRange(decoded, outputStart - zeros, decoded.length);
    }

    /**
     * Divides a number, represented as an array of bytes each containing a single digit
     * in the specified base, by the given divisor. The given number is modified in-place
     * to contain the quotient, and the return value is the remainder.
     *
     * @param number     the number to divide
     * @param firstDigit the index within the array of the first non-zero digit
     *                   (this is used for optimization by skipping the leading zeros)
     * @param base       the base in which the number's digits are represented (up to 256)
     * @param divisor    the number to divide by (up to 256)
     * @return the remainder of the division operation
     */
    private static byte divmod(byte[] number, int firstDigit, int base, int divisor) {
        // this is just long division which accounts for the base of the input digits
        int remainder = 0;
        for (int i = firstDigit; i < number.length; i++) {
            int digit = (int) number[i] & 0xFF;
            int temp = remainder * base + digit;
            number[i] = (byte) (temp / divisor);
            remainder = temp % divisor;
        }
        return (byte) remainder;
    }

    /**
     * Get the Maximum Item per Page need to display.
     *
     * @return maximumItemsPerPage need to display.
     */
    public static int getMaximumItemPerPage() {

        int maximumItemsPerPage = IdentityCoreConstants.DEFAULT_MAXIMUM_ITEMS_PRE_PAGE;
        String maximumItemsPerPagePropertyValue =
                IdentityUtil.getProperty(IdentityCoreConstants.MAXIMUM_ITEMS_PRE_PAGE_PROPERTY);
        if (StringUtils.isNotBlank(maximumItemsPerPagePropertyValue)) {
            try {
                maximumItemsPerPage = Integer.parseInt(maximumItemsPerPagePropertyValue);
            } catch (NumberFormatException e) {
                maximumItemsPerPage = IdentityCoreConstants.DEFAULT_MAXIMUM_ITEMS_PRE_PAGE;
                log.warn("Error occurred while parsing the 'MaximumItemsPerPage' property value in identity.xml.", e);
            }
        }
        return maximumItemsPerPage;
    }

    /**
     * Get the Maximum Actions per Action Type to be configured.
     *
     * @return maximumItemsPerPage need to display.
     */
    public static int getMaximumActionsPerActionType() {

        int maximumActionsPerActionType = IdentityCoreConstants.DEFAULT_MAXIMUM_ACTIONS_PER_TYPE;
        String maximumActionsPerActionTypePropertyValue =
                IdentityUtil.getProperty(IdentityCoreConstants.MAXIMUM_ACTIONS_PER_TYPE_PROPERTY);
        if (StringUtils.isNotBlank(maximumActionsPerActionTypePropertyValue)) {
            try {
                maximumActionsPerActionType = Integer.parseInt(maximumActionsPerActionTypePropertyValue);
            } catch (NumberFormatException e) {
                log.warn("Error occurred while parsing the 'maximumActionsPerActionType' property value " +
                        "in identity.xml.", e);
            }
        }
        return maximumActionsPerActionType;
    }

    /**
     * Get the Maximum Webhooks per Tenant to be configured.
     *
     * @return maximumWebhooksPerTenant which can be configured.
     */
    public static int getMaximumWebhooksPerTenant() {

        int maximumWebhooksPerTenant = IdentityCoreConstants.DEFAULT_MAXIMUM_WEBHOOKS_PER_TENANT;
        String maximumWebhooksPerTenantPropertyValue =
                IdentityUtil.getProperty(IdentityCoreConstants.MAXIMUM_WEBHOOKS_PER_TENANT_PROPERTY);
        if (StringUtils.isNotBlank(maximumWebhooksPerTenantPropertyValue)) {
            try {
                maximumWebhooksPerTenant = Integer.parseInt(maximumWebhooksPerTenantPropertyValue);
            } catch (NumberFormatException e) {
                log.warn("Error occurred while parsing the 'maximumWebhooksPerTenant' property value in " +
                        "identity.xml.", e);
            }
        }
        return maximumWebhooksPerTenant;
    }

    /**
     * Get the Default Items per Page needed to display.
     *
     * @return defaultItemsPerPage need to display.
     */
    public static int getDefaultItemsPerPage() {

        int defaultItemsPerPage = IdentityCoreConstants.DEFAULT_ITEMS_PRE_PAGE;
        try {
            String defaultItemsPerPageProperty = IdentityUtil.getProperty(IdentityCoreConstants
                    .DEFAULT_ITEMS_PRE_PAGE_PROPERTY);
            if (StringUtils.isNotBlank(defaultItemsPerPageProperty)) {
                int defaultItemsPerPageConfig = Integer.parseInt(defaultItemsPerPageProperty);
                if (defaultItemsPerPageConfig > 0) {
                    defaultItemsPerPage = defaultItemsPerPageConfig;
                }
            }
        } catch (NumberFormatException e) {
            // Ignore.
        }
        return defaultItemsPerPage;
    }

    /**
     * Get the Maximum Users List per Role needed to display.
     *
     * @return maxUsersListPerRole need to display. If the property is invalid, falls back to the lower bound value.
     */
    public static int getMaximumUsersListPerRole() {

        String maxUsersListPerRolePropertyValue = IdentityUtil.getProperty(
                IdentityCoreConstants.MAXIMUM_USERS_LIST_PER_ROLE_PROPERTY);

        if (StringUtils.isBlank(maxUsersListPerRolePropertyValue)) {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Missing 'MaximumUsersListPerRole' property. Using lower bound value %d.",
                        USERS_LIST_PER_ROLE_LOWER_BOUND));
            }
            return USERS_LIST_PER_ROLE_LOWER_BOUND;
        }

        try {
            int maxUsersListPerRole = Integer.parseInt(maxUsersListPerRolePropertyValue);
            if (maxUsersListPerRole >= USERS_LIST_PER_ROLE_LOWER_BOUND) {
                return maxUsersListPerRole;
            }
            if (log.isDebugEnabled()) {
                log.debug(String.format("Configured 'MaximumUsersListPerRole' value %s " +
                        "is below the recommended minimum.", maxUsersListPerRolePropertyValue));
            }
        } catch (NumberFormatException e) {
            log.debug("Error occurred while parsing the 'MaximumUsersListPerRole' property.", e);
        }
        if (log.isDebugEnabled()) {
            log.debug(String.format("Falling back to the lower bound value %d.", USERS_LIST_PER_ROLE_LOWER_BOUND));
        }
        return USERS_LIST_PER_ROLE_LOWER_BOUND;
    }

    /**
     * Check with authorization manager whether groups vs roles separation config is set to true.
     *
     * @return Where groups vs separation enabled or not.
     */
    public static boolean isGroupsVsRolesSeparationImprovementsEnabled() {

        try {
            UserRealm userRealm = AdminServicesUtil.getUserRealm();
            if (userRealm == null) {
                log.warn("Unable to find the user realm, thus GroupAndRoleSeparationEnabled is set as FALSE.");
                return Boolean.FALSE;
            }
            if (groupsVsRolesSeparationImprovementsEnabled == null) {
                groupsVsRolesSeparationImprovementsEnabled = UserCoreUtil.isGroupsVsRolesSeparationImprovementsEnabled(
                        userRealm.getRealmConfiguration());
            }
            return groupsVsRolesSeparationImprovementsEnabled;

        } catch (UserStoreException | CarbonException e) {
            log.warn("Property value parsing error: GroupAndRoleSeparationEnabled, thus considered as FALSE");
            return Boolean.FALSE;
        }
    }

    /**
     * Check with authorization manager whether show role claim on group role separation config is set to true.
     *
     * @return Where show role claim on group role separation enabled or not.
     */
    public static boolean isShowLegacyRoleClaimOnGroupRoleSeparationEnabled() {

        try {
            UserRealm userRealm = AdminServicesUtil.getUserRealm();
            if (userRealm == null) {
                log.warn("Unable to find the user realm, thus ShowRoleClaimOnGroupRoleSeparationEnabled is set as FALSE.");
                return Boolean.FALSE;
            }
            if (showLegacyRoleClaimOnGroupRoleSeparationEnabled == null) {
                showLegacyRoleClaimOnGroupRoleSeparationEnabled =
                        UserCoreUtil.isShowLegacyRoleClaimOnGroupRoleSeparationEnabled(
                                userRealm.getRealmConfiguration());
            }
            return showLegacyRoleClaimOnGroupRoleSeparationEnabled;
        } catch (UserStoreException | CarbonException e) {
            log.warn("Property value parsing error: ShowRoleClaimOnGroupRoleSeparationEnabled, thus considered as FALSE");
            return Boolean.FALSE;
        }
    }

    /**
     * With group role separation, user roles are separated into groups and internal roles and, to support backward
     * compatibility, the legacy wso2.role claim still returns both groups and internal roles. This method provides
     * claim URIs of these group, role claims.
     *
     * @return An unmodifiable set of claim URIs which contain user groups, roles, or both.
     */
    public static Set<String> getRoleGroupClaims() {

        Set<String> roleGroupClaimURIs = new HashSet<>();
        roleGroupClaimURIs.add(UserCoreConstants.ROLE_CLAIM);
        if (IdentityUtil.isGroupsVsRolesSeparationImprovementsEnabled()) {
            roleGroupClaimURIs.add(UserCoreConstants.INTERNAL_ROLES_CLAIM);
            roleGroupClaimURIs.add(UserCoreConstants.USER_STORE_GROUPS_CLAIM);
        }
        return Collections.unmodifiableSet(roleGroupClaimURIs);
    }

    /**
     * With group role separation, user roles are separated into groups and internal roles and, to support backward
     * compatibility, the legacy wso2.role claim still returns both groups and internal roles. This method provides
     * the claim URI which contain internal roles, or both groups and roles in a backward compatible manner.
     *
     * @return Claim URI for the user groups, or both groups and roles based on the backward compatibility.
     */
    public static String getLocalGroupsClaimURI() {

        return IdentityUtil.isGroupsVsRolesSeparationImprovementsEnabled() ?
                UserCoreConstants.INTERNAL_ROLES_CLAIM : UserCoreConstants.ROLE_CLAIM;
    }

    /**
     * This will return a map of system roles and the list of scopes configured for each system role.
     *
     * @return A map of system roles against the scopes list.
     */
    public static Map<String, Set<String>> getSystemRolesWithScopes() {

        Map<String, Set<String>> systemRolesWithScopes = new HashMap<>(Collections.emptyMap());
        IdentityConfigParser configParser = IdentityConfigParser.getInstance();
        OMElement systemRolesConfig = configParser
                .getConfigElement(IdentityConstants.SystemRoles.SYSTEM_ROLES_CONFIG_ELEMENT);
        if (systemRolesConfig == null) {
            if (log.isDebugEnabled()) {
                log.debug(
                        "'" + IdentityConstants.SystemRoles.SYSTEM_ROLES_CONFIG_ELEMENT + "' config cannot be found.");
            }
            return Collections.emptyMap();
        }

        Iterator roleIdentifierIterator = systemRolesConfig
                .getChildrenWithLocalName(IdentityConstants.SystemRoles.ROLE_CONFIG_ELEMENT);
        if (roleIdentifierIterator == null) {
            if (log.isDebugEnabled()) {
                log.debug("'" + IdentityConstants.SystemRoles.ROLE_CONFIG_ELEMENT + "' config cannot be found.");
            }
            return Collections.emptyMap();
        }

        while (roleIdentifierIterator.hasNext()) {
            OMElement roleIdentifierConfig = (OMElement) roleIdentifierIterator.next();
            String roleName = roleIdentifierConfig.getFirstChildWithName(
                    new QName(IdentityCoreConstants.IDENTITY_DEFAULT_NAMESPACE,
                            IdentityConstants.SystemRoles.ROLE_NAME_CONFIG_ELEMENT)).getText();

            OMElement mandatoryScopesIdentifierIterator = roleIdentifierConfig.getFirstChildWithName(
                    new QName(IdentityCoreConstants.IDENTITY_DEFAULT_NAMESPACE,
                            IdentityConstants.SystemRoles.ROLE_MANDATORY_SCOPES_CONFIG_ELEMENT));
            Iterator scopeIdentifierIterator = mandatoryScopesIdentifierIterator
                    .getChildrenWithLocalName(IdentityConstants.SystemRoles.ROLE_SCOPE_CONFIG_ELEMENT);

            Set<String> scopes = new HashSet<>();
            while (scopeIdentifierIterator.hasNext()) {
                OMElement scopeIdentifierConfig = (OMElement) scopeIdentifierIterator.next();
                String scopeName = scopeIdentifierConfig.getText();
                if (StringUtils.isNotBlank(scopeName)) {
                    scopes.add(scopeName.trim().toLowerCase());
                }
            }
            if (StringUtils.isNotBlank(roleName)) {
                systemRolesWithScopes.put(roleName.trim(), scopes);
            }
        }
        return systemRolesWithScopes;
    }

    /**
     * This will return a map of system roles and the list of api resource collection configured for each system role.
     *
     * @return A map of system roles against the api resource collection list.
     */
    public static Map<String, Set<String>> getSystemRolesWithAPIResources() {

        Map<String, Set<String>> systemRolesWithAPIResources = new HashMap<>();
        IdentityConfigParser configParser = IdentityConfigParser.getInstance();
        OMElement systemRolesConfig = configParser.getConfigElement(IdentityConstants.SystemRoles
                .SYSTEM_ROLES_CONFIG_ELEMENT);

        if (systemRolesConfig != null) {
            Iterator roleIdentifierIterator = systemRolesConfig.getChildrenWithLocalName(IdentityConstants.SystemRoles
                    .ROLE_CONFIG_ELEMENT);

            while (roleIdentifierIterator != null && roleIdentifierIterator.hasNext()) {
                OMElement roleIdentifierConfig = (OMElement) roleIdentifierIterator.next();
                String roleName = roleIdentifierConfig.getFirstChildWithName(
                        new QName(IdentityCoreConstants.IDENTITY_DEFAULT_NAMESPACE,
                                IdentityConstants.SystemRoles.ROLE_NAME_CONFIG_ELEMENT)).getText();

                OMElement mandatoryApiResourcesIdentifier = roleIdentifierConfig.getFirstChildWithName(
                        new QName(IdentityCoreConstants.IDENTITY_DEFAULT_NAMESPACE,
                                IdentityConstants.SystemRoles.ROLE_MANDATORY_API_RESOURCES_CONFIG_ELEMENT));

                if (mandatoryApiResourcesIdentifier == null) {
                    continue;
                }

                Iterator apiResourceIdentifier = mandatoryApiResourcesIdentifier.getChildrenWithLocalName(
                        IdentityConstants.SystemRoles.API_RESOURCE_CONFIG_ELEMENT);

                Set<String> apiResourceCollections = new HashSet<>();
                while (apiResourceIdentifier != null && apiResourceIdentifier.hasNext()) {
                    OMElement apiResourceConfig = (OMElement) apiResourceIdentifier.next();
                    String apiResource = apiResourceConfig.getText();
                    if (StringUtils.isNotBlank(apiResource)) {
                        apiResourceCollections.add(apiResource.trim());
                    }
                }
                if (StringUtils.isNotBlank(roleName)) {
                    systemRolesWithAPIResources.put(roleName.trim(), apiResourceCollections);
                }
            }
        } else {
            log.debug(IdentityConstants.SystemRoles.SYSTEM_ROLES_CONFIG_ELEMENT + " config cannot be found.");
        }
        return systemRolesWithAPIResources;
    }

    /**
     * Check whether the system roles are enabled in the environment.
     *
     * @return {@code true} if the the system roles are enabled.
     */
    public static boolean isSystemRolesEnabled() {

        return Boolean.parseBoolean(
                IdentityUtil.getProperty(IdentityConstants.SystemRoles.SYSTEM_ROLES_ENABLED_CONFIG_ELEMENT));
    }

    /**
     * Check whether the system is set to use Claim locality to store localization code as its legacy implementation.
     * Or set to use Claim local to store localization
     *
     * @return 'http://wso2.org/claims/locality' or 'http://wso2.org/claims/local' based on the configuration.
     */
    public static String getClaimUriLocale() {

        if (Boolean.parseBoolean(IdentityUtil.getProperty("UseLegacyLocalizationClaim"))) {
            return "http://wso2.org/claims/locality";
        } else {
            return "http://wso2.org/claims/local";
        }
    }

    /**
     * Retrieves the unique user id of the given username. If the unique user id is not available, generate an id and
     * update the userid claim in read/write userstores.
     *
     * @param tenantId        Id of the tenant domain of the user.
     * @param userStoreDomain Userstore of the user.
     * @param username        Username.
     * @return Unique user id of the user.
     * @throws IdentityException When error occurred while retrieving the user id.
     */
    @Deprecated
    public static String resolveUserIdFromUsername(int tenantId, String userStoreDomain, String username) throws
            IdentityException {

        try {
            if (StringUtils.isEmpty(userStoreDomain)) {
                userStoreDomain = IdentityCoreServiceDataHolder.getInstance().getRealmService().getTenantUserRealm(tenantId).
                        getRealmConfiguration().getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME);
                if (StringUtils.isEmpty(userStoreDomain)) {
                    userStoreDomain = UserCoreConstants.PRIMARY_DEFAULT_DOMAIN_NAME;
                }
            }
            org.wso2.carbon.user.api.UserStoreManager userStoreManager = getUserStoreManager(tenantId, userStoreDomain);
            try {
                if (userStoreManager instanceof AbstractUserStoreManager) {
                    String userId = ((AbstractUserStoreManager) userStoreManager).getUserIDFromUserName(username);

                    // If the user id could not be resolved, probably user does not exist in the user store.
                    if (StringUtils.isBlank(userId)) {
                        if (log.isDebugEnabled()) {
                            log.debug("User id could not be resolved for username: " + username + " in user store " +
                                    "domain: " + userStoreDomain + " and tenant with id: " + tenantId + ". Probably " +
                                    "user does not exist in the user store. Hence trying to resolve user from the " +
                                    "resident organization.");
                        }
                        Tenant tenant = IdentityCoreServiceDataHolder.getInstance().getRealmService()
                                .getTenantManager().getTenant(tenantId);
                        if (tenant == null) {
                            return userId;
                        }
                        String associatedOrganizationUUID = tenant.getAssociatedOrganizationUUID();
                        if (StringUtils.isNotBlank(associatedOrganizationUUID)) {
                            // Trying to resolve the user from the Resident Organization if the user is not found in
                            // the user store of the current organization.
                            Optional<User> user = IdentityCoreServiceDataHolder.getInstance()
                                    .getOrganizationUserResidentResolverService().resolveUserFromResidentOrganization(
                                            username, null, associatedOrganizationUUID);
                            if (user.isPresent()) {
                                return user.get().getUserID();
                            }
                        }
                    }
                    return userId;
                }
                if (log.isDebugEnabled()) {
                    log.debug("Provided user store manager for the user: " + username + ", is not an instance of the " +
                            "AbstractUserStore manager");
                }
                throw new IdentityException("Unable to get the unique id of the user: " + username + ".");
            } catch (org.wso2.carbon.user.core.UserStoreException | OrganizationManagementException e) {
                if (log.isDebugEnabled()) {
                    log.debug("Error occurred while resolving Id for the user: " + username, e);
                }
                throw new IdentityException("Error occurred while resolving Id for the user: " + username, e);
            }
        } catch (UserStoreException e) {
            throw new IdentityException("Error occurred while retrieving the userstore manager to resolve Id for " +
                    "the user: " + username, e);
        }
    }

    private static org.wso2.carbon.user.api.UserStoreManager getUserStoreManager(int tenantId, String userStoreDomain)
            throws UserStoreException {

        org.wso2.carbon.user.api.UserStoreManager userStoreManager =
                IdentityCoreServiceDataHolder.getInstance().getRealmService().getTenantUserRealm(tenantId)
                        .getUserStoreManager();
        if (userStoreManager instanceof org.wso2.carbon.user.core.UserStoreManager) {
            return ((org.wso2.carbon.user.core.UserStoreManager) userStoreManager).getSecondaryUserStoreManager(
                    userStoreDomain);
        }
        if (log.isDebugEnabled()) {
            String debugLog = String.format(
                    "Unable to resolve the corresponding user store manager for the domain: %s, " +
                            "as the provided user store manager: %s, is not an instance of " +
                            "org.wso2.carbon.user.core.UserStoreManager. Therefore returning the user store manager: %s," +
                            " from the realm.", userStoreDomain, userStoreManager.getClass(),
                    userStoreManager.getClass());
            log.debug(debugLog);
        }
        return userStoreManager;
    }

    /**
     * Read configuration elements defined as lists from the identity.xml
     *
     * @param key Element Name as specified from the parent elements in the XML structure.
     *            To read the element value of b in {@code <a><b>t1</b><b>t2</b></a>},
     *            the property name should be passed as "a.b" to get a list of b
     * @return String list from the config element passed in as key.
     */
    public static List<String> getPropertyAsList(String key) {

        List<String> propertyList = new ArrayList<>();
        Object value = configuration.get(key);

        if (value == null) {
            return propertyList;
        }
        if (value instanceof List) {
            List rawProps = (List) value;
            for (Object rawProp: rawProps ) {
                if (rawProp instanceof String) {
                    propertyList.add((String) rawProp);
                } else {
                    propertyList.add(String.valueOf(rawProp));
                }
            }
        } else if (value instanceof String) {
            propertyList.add((String) value);
        } else {
            propertyList.add(String.valueOf(value));
        }
        return propertyList;
    }

    /**
     * Get validity period configured for the authentication context.
     *
     * @return Validity period in minutes.
     */
    public static long getAuthenticationContextValidityPeriod() {

        // We consider auth context validity period is equal to temp data cleanup timeout.
        return getTempDataCleanUpTimeout();
    }

    /**
     * Get the initiator id.
     *
     * @param userName     Username of the initiator.
     * @param tenantDomain Tenant domain of the initiator.
     * @return User id of the initiator.
     */
    public static String getInitiatorId(String userName, String tenantDomain) {

        String userId = PrivilegedCarbonContext.getThreadLocalCarbonContext().getUserId();
        if (StringUtils.isBlank(userId)) {
            String userStoreDomain = UserCoreUtil.extractDomainFromName(userName);
            String username = UserCoreUtil.removeDomainFromName(userName);
            int tenantId = IdentityTenantUtil.getTenantId(tenantDomain);
            try {
                return IdentityUtil.resolveUserIdFromUsername(tenantId, userStoreDomain, username);
            } catch (IdentityException e) {
                // Below log is changed to a debug log hence the exception is not thrown
                // from the upper layer and handled gracefully.
                log.debug("Error occurred while resolving Id for the user: " + username);
            }
        }
        return userId;
    }

    /**
     * Read the SCIM User Endpoint Maximum Items Per Page is enabled config and returns it.
     *
     * @return If SCIM User Endpoint Maximum Items Per Page is enabled.
     */
    public static boolean isSCIM2UserMaxItemsPerPageEnabled() {

        String scim2UserMaxItemsPerPageEnabledProperty =
                IdentityUtil.getProperty(IdentityCoreConstants.SCIM2_USER_MAX_ITEMS_PER_PAGE_ENABLED);

        if (StringUtils.isBlank(scim2UserMaxItemsPerPageEnabledProperty)) {
            return true;
        }
        return Boolean.parseBoolean(scim2UserMaxItemsPerPageEnabledProperty);
    }

    /**
     * Validates the provided signature for the given data using the public key of a specified tenant.
     *
     * The method retrieves the public key for the tenant from the certificate stored in the tenant's keystore.
     * If a context is provided, the method attempts to retrieve the certificate within that context.
     *
     * @param data        The data to validate the signature against.
     * @param signature   The signature to be validated.
     * @param tenantDomain The domain name of the tenant whose public key should be used for validation.
     * @param context     The optional context for retrieving the tenant's certificate (can be null or blank).
     * @return True if the signature is valid; false otherwise.
     * @throws SignatureException If an error occurs while validating the signature or accessing tenant data.
     */
    public static boolean validateSignatureFromTenant(String data, byte[] signature, String tenantDomain,
                                                      String context) throws SignatureException {

        // Retrieve tenant ID based on the tenant domain
        int tenantId = IdentityTenantUtil.getTenantId(tenantDomain);
        try {
            // Initialize the tenant's registry
            IdentityTenantUtil.initializeRegistry(tenantId);

            // Retrieve the tenant's public key
            PublicKey publicKey;
            if (StringUtils.isBlank(context)) {
                // Fetch certificate without context if context is null or blank
                publicKey = IdentityKeyStoreResolver.getInstance()
                        .getCertificate(tenantDomain, null)
                        .getPublicKey();
            } else {
                try {
                    // Fetch certificate within the provided context
                    Certificate certificate = IdentityKeyStoreResolver.getInstance()
                            .getCertificate(tenantDomain, null, context);
                    publicKey = certificate.getPublicKey();
                } catch (IdentityKeyStoreResolverException e) {
                    if (ERROR_RETRIEVING_TENANT_CONTEXT_PUBLIC_CERTIFICATE_KEYSTORE_NOT_EXIST.getCode()
                            .equals(e.getErrorCode())) {
                        // Context keystore not exits, hence return validation as false.
                        return false;
                    } else {
                        throw new SignatureException("Error while validating the signature for tenant: "
                                + tenantDomain, e);
                    }
                }
            }

            // Validate the signature using the retrieved public key
            return SignatureUtil.validateSignature(data, signature, publicKey);
        } catch (IdentityException e) {
            // Log and throw an exception if an error occurs
            throw new SignatureException("Error while validating the signature for tenant: " + tenantDomain, e);
        }
    }

    /**
     * Validates the signature of the given data for the specified tenant domain.
     *
     * @param data         The data to be verified.
     * @param signature    The signature to be verified.
     * @param tenantDomain The tenant domain to which the data belongs.
     * @return true if the signature is valid, false otherwise.
     * @throws SignatureException If an error occurs during the signature validation process.
     */
    public static boolean validateSignatureFromTenant(String data, byte[] signature, String tenantDomain)
            throws SignatureException {

        return validateSignatureFromTenant(data, signature, tenantDomain, null);
    }

    /**
     * Signs the given data using the private key of the specified tenant.
     *
     * For super tenant domains, the default private key is used. For other tenants, the method retrieves the private
     * key from the tenant's keystore. If a context is provided, it will attempt to retrieve the private key associated
     * with that context.
     *
     * @param data         The data to be signed.
     * @param tenantDomain The domain name of the tenant whose private key will be used for signing.
     * @param context      The optional context for retrieving the tenant's private key (can be null or blank).
     * @return A byte array containing the signature for the provided data.
     * @throws SignatureException If an error occurs while retrieving the private key or signing the data.
     */
    public static byte[] signWithTenantKey(String data, String tenantDomain, String context) throws SignatureException {

        // Get tenant ID from tenant domain
        int tenantId = IdentityTenantUtil.getTenantId(tenantDomain);
        KeyStoreManager keyStoreManager = KeyStoreManager.getInstance(tenantId);
        PrivateKey privateKey;

        if (MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
            try {
                String tenantKeyStoreName = IdentityKeyStoreResolverUtil.buildTenantKeyStoreName(tenantDomain, context);
                // Retrieve private key from the tenant's keystore
                if (StringUtils.isBlank(context)) {
                    // Retrieve default private key for the super tenant
                    privateKey = keyStoreManager.getDefaultPrivateKey();
                } else {
                    privateKey = (PrivateKey) keyStoreManager.getPrivateKey(tenantKeyStoreName,
                            tenantDomain +
                                    IdentityKeyStoreResolverConstants.KEY_STORE_CONTEXT_SEPARATOR + context);
                }

            } catch (Exception e) {
                throw new SignatureException(String.format(
                        IdentityKeyStoreResolverConstants.ErrorMessages.ERROR_CODE_ERROR_RETRIEVING_TENANT_PRIVATE_KEY
                                .getDescription(),
                        tenantDomain), e);
            }
        } else {
            try {
                // Build tenant keystore name
                String tenantKeyStoreName = IdentityKeyStoreResolverUtil.buildTenantKeyStoreName(tenantDomain, context);

                // Initialize the tenant's registry
                IdentityTenantUtil.initializeRegistry(tenantId);

                // Retrieve private key from the tenant's keystore
                if (StringUtils.isBlank(context)) {
                    privateKey = (PrivateKey) keyStoreManager.getPrivateKey(tenantKeyStoreName, tenantDomain);
                } else {
                    privateKey = (PrivateKey) keyStoreManager.getPrivateKey(tenantKeyStoreName,
                            tenantDomain +
                                    IdentityKeyStoreResolverConstants.KEY_STORE_CONTEXT_SEPARATOR + context);
                }
            } catch (IdentityException e) {
                throw new SignatureException("Error while retrieving the private key for tenant: " + tenantDomain, e);
            }
        }

        // Sign the data with the retrieved private key
        return SignatureUtil.doSignature(data, privateKey);
    }

    /**
     * Sign the given data for the specified tenant domain.
     *
     * @param data         The data to be signed.
     * @param tenantDomain The tenant domain to which the data belongs.
     * @return The signature of the data.
     * @throws SignatureException If an error occurs during the signature generation process.
     */
    public static byte[] signWithTenantKey(String data, String tenantDomain) throws SignatureException {

        return signWithTenantKey(data, tenantDomain, null);
    }

    /**
     * Check whether the agent identity is enabled.
     * @return
     */
    public static boolean isAgentIdentityEnabled() {

        if (IdentityUtil.getProperty(AGENT_IDENTITY_ENABLE) != null) {
            return Boolean.parseBoolean(IdentityUtil.getProperty(AGENT_IDENTITY_ENABLE));
        }
        return false;
    }

    /**
     * Get the agent identity userstore name.
     * If the property is not set, it will return the default agent identity userstore name.
     * @return Agent identity userstore name.
     */
    public static String getAgentIdentityUserstoreName() {

        String userStoreName = IdentityUtil.getProperty(AGENT_IDENTITY_USERSTORE_NAME);
        if (StringUtils.isBlank(userStoreName)) {
            userStoreName = DEFAULT_AGENT_IDENTITY_USERSTORE_NAME;
        }
        return userStoreName;
    }
}
