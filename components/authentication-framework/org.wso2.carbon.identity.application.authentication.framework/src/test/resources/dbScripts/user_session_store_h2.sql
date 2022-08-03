CREATE TABLE IF NOT EXISTS IDN_AUTH_USER (
    USER_ID VARCHAR(255) NOT NULL,
    USER_NAME VARCHAR(255) NOT NULL,
    TENANT_ID INTEGER NOT NULL,
    DOMAIN_NAME VARCHAR(255) NOT NULL,
    IDP_ID INTEGER NOT NULL,
    PRIMARY KEY (USER_ID),
    CONSTRAINT USER_STORE_CONSTRAINT UNIQUE (USER_NAME, TENANT_ID, DOMAIN_NAME, IDP_ID)
);

CREATE TABLE IF NOT EXISTS IDN_AUTH_USER_SESSION_MAPPING (
    USER_ID VARCHAR(255) NOT NULL,
    SESSION_ID VARCHAR(255) NOT NULL,
    CONSTRAINT USER_SESSION_STORE_CONSTRAINT UNIQUE (USER_ID, SESSION_ID)
);

CREATE INDEX IDX_USER_ID ON IDN_AUTH_USER_SESSION_MAPPING (USER_ID);
CREATE INDEX IDX_SESSION_ID ON IDN_AUTH_USER_SESSION_MAPPING (SESSION_ID);

CREATE TABLE IF NOT EXISTS IDN_AUTH_SESSION_META_DATA (
    SESSION_ID VARCHAR (100) NOT NULL,
    PROPERTY_TYPE VARCHAR (100) NOT NULL,
    `VALUE` VARCHAR (255) NOT NULL,
    PRIMARY KEY (SESSION_ID, PROPERTY_TYPE, `VALUE`)
);

CREATE TABLE IF NOT EXISTS IDN_AUTH_SESSION_APP_INFO (
    SESSION_ID VARCHAR (100) NOT NULL,
    SUBJECT VARCHAR (100) NOT NULL,
    APP_ID INTEGER NOT NULL,
    INBOUND_AUTH_TYPE VARCHAR (255) NOT NULL,
    PRIMARY KEY (SESSION_ID, SUBJECT, APP_ID, INBOUND_AUTH_TYPE)
);

CREATE INDEX IDX_AUTH_SAI_UN_AID_SID ON IDN_AUTH_SESSION_APP_INFO (SUBJECT, APP_ID, SESSION_ID);
CREATE INDEX IDX_AUTH_SAI_AID ON IDN_AUTH_SESSION_APP_INFO (APP_ID);
