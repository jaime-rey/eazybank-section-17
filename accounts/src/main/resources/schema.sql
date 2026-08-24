CREATE TABLE IF NOT EXISTS `customer` (
  `customer_id`   int AUTO_INCREMENT PRIMARY KEY,
  `name`          varchar(100) NOT NULL,
  `email`         varchar(100) NOT NULL,
  `mobile_number` varchar(20)  NOT NULL,
  `version`       bigint       NOT NULL DEFAULT 0,
  `created_at`    date         NOT NULL,
  `created_by`    varchar(20)  NOT NULL,
  `updated_at`    date         DEFAULT NULL,
  `updated_by`    varchar(20)  DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS `accounts` (
  `customer_id`      int          NOT NULL,
  `account_number`   int AUTO_INCREMENT PRIMARY KEY,
  `account_type`     varchar(100) NOT NULL,
  `branch_address`   varchar(200) NOT NULL,
  `communication_sw` BOOLEAN,
  `version`          bigint       NOT NULL DEFAULT 0,
  `created_at`       date         NOT NULL,
  `created_by`       varchar(20)  NOT NULL,
  `updated_at`       date         DEFAULT NULL,
  `updated_by`       varchar(20)  DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS `beneficiary` (
  `id`              int AUTO_INCREMENT PRIMARY KEY,
  `document_number` varchar(50)  NOT NULL,
  `full_name`       varchar(100) NOT NULL,
  `version`         bigint       NOT NULL DEFAULT 0,
  `created_at`      date         NOT NULL,
  `created_by`      varchar(20)  NOT NULL,
  `updated_at`      date         DEFAULT NULL,
  `updated_by`      varchar(20)  DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS `account_beneficiary` (
  `account_number` int           NOT NULL,
  `beneficiary_id` int           NOT NULL,
  `percentage`     DECIMAL(5, 2) NOT NULL,
  `version`        bigint        NOT NULL DEFAULT 0,
  `created_at`     date          NOT NULL,
  `created_by`     varchar(20)   NOT NULL,
  `updated_at`     date          DEFAULT NULL,
  `updated_by`     varchar(20)   DEFAULT NULL,
  PRIMARY KEY (`account_number`, `beneficiary_id`),
  FOREIGN KEY (`account_number`) REFERENCES `accounts` (`account_number`),
  FOREIGN KEY (`beneficiary_id`) REFERENCES `beneficiary` (`id`)
);
