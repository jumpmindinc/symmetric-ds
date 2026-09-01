--
-- Licensed to JumpMind Inc under one or more contributor
-- license agreements.  See the NOTICE file distributed
-- with this work for additional information regarding
-- copyright ownership.  JumpMind Inc licenses this file
-- to you under the GNU Affero General Public License, version 3.0 (AGPLv3)
-- (the "License"); you may not use this file except in compliance
-- with the License.
--
-- You should have received a copy of the GNU Affero General Public License,
-- version 3.0 (AGPLv3) along with this library; if not, see
-- <http://www.gnu.org/licenses/>.
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
-- KIND, either express or implied.  See the License for the
-- specific language governing permissions and limitations
-- under the License.
--


CREATE TABLE `test_very_long_table_name_1234`(
    `id` VARCHAR(100) NOT NULL,
    PRIMARY KEY (`id`)
);

CREATE TABLE `test_extract_table`(
    `id` INTEGER NOT NULL,
    `varchar_value` VARCHAR(255) NULL,
    `longvarchar_value` MEDIUMTEXT NULL,
    `timestamp_value` DATETIME,
    `date_value` DATE,
    `bit_value` BIT,
    `bigint_value` BIGINT,
    `decimal_value` DECIMAL(18,15),
    PRIMARY KEY (`id`)
);

CREATE TABLE `test_triggers_table`(
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `string_one_value` VARCHAR(50) NULL,
    `string_two_value` VARCHAR(255) NULL,
    `long_string_value` VARCHAR(255) NULL,
    `time_value` DATETIME,
    `date_value` DATE,
    `boolean_value` SMALLINT,
    `bigint_value` BIGINT,
    `decimal_value` DECIMAL,
    PRIMARY KEY (`id`)
);

CREATE TABLE `test_dataloader_table`(
    `string_value` VARCHAR(50) NULL,
    `string_required_value` VARCHAR(50) NOT NULL,
    `char_value` CHAR(50) NULL,
    `char_required_value` CHAR(50) NOT NULL,
    `date_value` DATE,
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `time_value` DATETIME,
    `boolean_value` BIT,
    `integer_value` INTEGER,
    `decimal_value` DECIMAL(10,2),
    `double_value` DOUBLE,
    PRIMARY KEY (`id`)
);

CREATE TABLE `test_order_header`(
    `order_id` VARCHAR(50) NOT NULL,
    `customer_id` INTEGER NOT NULL,
    `status` CHAR(1) NULL,
    `deliver_date` DATE,
    PRIMARY KEY (`order_id`)
);

CREATE TABLE `test_column_mapping`(
    `id` VARCHAR(50) NOT NULL,
    `column1` VARCHAR(50) NULL,
    `column2` VARCHAR(50) NULL,
    `field1` INTEGER,
    `time1` DATETIME,
    `another_id_column` VARCHAR(50) NULL,
    PRIMARY KEY (`id`)
);

CREATE TABLE `test_order_detail`(
    `order_id` VARCHAR(50) NOT NULL,
    `line_number` INTEGER NOT NULL,
    `item_type` CHAR(5) NOT NULL,
    `item_id` VARCHAR(20) NOT NULL,
    `quantity` INTEGER,
    `price` DECIMAL(10,2),
    PRIMARY KEY (`order_id`, `line_number`)
);

CREATE TABLE `test_store_status`(
    `store_id` CHAR(5) NOT NULL,
    `register_id` CHAR(3) NOT NULL,
    `status` INTEGER,
    PRIMARY KEY (`store_id`, `register_id`)
);

CREATE TABLE `test_key_word`(
    `id` INTEGER NOT NULL,
    PRIMARY KEY (`id`)
);

CREATE TABLE `test_customer`(
    `customer_id` INTEGER NOT NULL,
    `name` VARCHAR(50) NOT NULL,
    `is_active` CHAR(1) NULL,
    `status` VARCHAR(20) NULL,
    `address` VARCHAR(50) NOT NULL,
    `city` VARCHAR(50) NOT NULL,
    `state` VARCHAR(50) NOT NULL,
    `zip` INTEGER,
    `entry_timestamp` DATETIME,
    `entry_time` TIME,
    `notes` LONGTEXT NULL,
    `icon` LONGBLOB NULL,
    PRIMARY KEY (`customer_id`)
);

CREATE TABLE `test_use_stream_lob`(
    `test_id` INTEGER NOT NULL,
    `test_clob` LONGTEXT NULL,
    `test_blob` LONGBLOB NULL,
    `test_varbinary` VARBINARY(254) NULL,
    `test_binary` BINARY(254) NULL,
    `test_longvarchar` MEDIUMTEXT NULL,
    `test_longvarbinary` MEDIUMBLOB NULL,
    PRIMARY KEY (`test_id`)
);

CREATE TABLE `test_use_capture_lob`(
    `test_id` INTEGER NOT NULL,
    `test_clob` LONGTEXT NULL,
    `test_blob` LONGBLOB NULL,
    `test_varbinary` VARBINARY(254) NULL,
    `test_binary` BINARY(254) NULL,
    `test_longvarchar` MEDIUMTEXT NULL,
    `test_longvarbinary` MEDIUMBLOB NULL,
    PRIMARY KEY (`test_id`)
);

CREATE TABLE `one_column_table`(
    `my_one_column` INTEGER NOT NULL,
    PRIMARY KEY (`my_one_column`)
);

CREATE TABLE `no_primary_key_table`(
    `one_column` INTEGER NOT NULL,
    `two_column` INTEGER NOT NULL,
    `three_column` VARCHAR(50) NOT NULL
);

CREATE TABLE `init_load_from_client_table`(
    `id` INTEGER NOT NULL,
    `data` VARCHAR(10) NULL,
    PRIMARY KEY (`id`)
);

CREATE TABLE `test_sync_incoming_batch`(
    `id` INTEGER NOT NULL,
    `data` VARCHAR(10) NULL,
    PRIMARY KEY (`id`)
);

CREATE TABLE `test_sync_column_level`(
    `id` INTEGER NOT NULL,
    `string_value` VARCHAR(50) NULL,
    `time_value` DATETIME,
    `date_value` DATE,
    `bigint_value` BIGINT,
    `decimal_value` DECIMAL(10,2),
    PRIMARY KEY (`id`)
);

CREATE TABLE `test_all_caps`(
    `all_caps_id` INTEGER NOT NULL,
    `name` VARCHAR(50) NOT NULL,
    PRIMARY KEY (`all_caps_id`)
);

CREATE TABLE `Test_Mixed_Case`(
    `Mixed_Case_Id` INTEGER NOT NULL,
    `Name` VARCHAR(50) NOT NULL,
    PRIMARY KEY (`Mixed_Case_Id`)
);

CREATE TABLE `test_xml_publisher`(
    `id1` INTEGER NOT NULL,
    `id2` INTEGER NOT NULL,
    `data1` VARCHAR(10) NULL,
    `data2` INTEGER,
    `data3` DATETIME,
    PRIMARY KEY (`id1`)
);

CREATE TABLE `test_add_dl_table_1`(
    `pk1` VARCHAR(100) NOT NULL,
    `pk2` VARCHAR(100) NOT NULL,
    `add1` DECIMAL(10,0),
    `add2` DECIMAL(10,2),
    `add3` INTEGER,
    `ovr1` DECIMAL(10,2),
    `ovr2` INTEGER,
    `ovr3` VARCHAR(10) NULL,
    `nada1` INTEGER,
    PRIMARY KEY (`pk1`, `pk2`)
);

CREATE TABLE `test_add_dl_table_2`(
    `pk1` VARCHAR(100) NOT NULL,
    `add1` DECIMAL(10,0),
    PRIMARY KEY (`pk1`)
);

CREATE TABLE `test_target_table_a`(
    `id_field` VARCHAR(100) NOT NULL,
    `desc_field` VARCHAR(254) NULL,
    PRIMARY KEY (`id_field`)
);

CREATE TABLE `test_target_table_b`(
    `id_field` VARCHAR(100) NOT NULL,
    `desc_field` VARCHAR(254) NULL,
    PRIMARY KEY (`id_field`)
);

CREATE TABLE `test_changing_column_name`(
    `id1` VARCHAR(100) NOT NULL,
    `test` DECIMAL(10,0),
    PRIMARY KEY (`id1`)
);

CREATE TABLE `test_routing_data_1`(
    `pk` INTEGER NOT NULL AUTO_INCREMENT,
    `routing_int` INTEGER,
    `routing_varchar` VARCHAR(10) NULL,
    `data_blob` LONGBLOB NULL,
    `my_time` DATETIME,
    PRIMARY KEY (`pk`)
);

CREATE TABLE `test_routing_data_2`(
    `pk` INTEGER NOT NULL AUTO_INCREMENT,
    `routing_int` INTEGER,
    `routing_varchar` VARCHAR(10) NULL,
    `data_blob` LONGBLOB NULL,
    `my_time` DATETIME,
    PRIMARY KEY (`pk`)
);

CREATE TABLE `test_routing_data_subtable`(
    `pk` INTEGER NOT NULL AUTO_INCREMENT,
    `fk` INTEGER,
    PRIMARY KEY (`pk`)
);

CREATE TABLE `test_lookup_table`(
    `column_one` VARCHAR(10) NULL,
    `column_two` VARCHAR(10) NULL
);

CREATE TABLE `test_transform_a`(
    `id_a` INTEGER NOT NULL AUTO_INCREMENT,
    `s1_a` VARCHAR(50) NULL,
    `s2_a` VARCHAR(255) NULL,
    `longstring_a` MEDIUMTEXT NULL,
    `time_a` DATETIME,
    `date_a` DATE,
    `boolean_a` BIT,
    `bigint_a` BIGINT,
    `decimal_a` DECIMAL,
    PRIMARY KEY (`id_a`)
);

CREATE TABLE `test_transform_b`(
    `id_b` INTEGER NOT NULL AUTO_INCREMENT,
    `s1_b` VARCHAR(50) NULL,
    `s2_b` VARCHAR(255) NULL,
    `longstring_b` MEDIUMTEXT NULL,
    `time_b` DATETIME,
    `date_b` DATE,
    `boolean_b` BIT,
    `bigint_b` BIGINT,
    `decimal_b` DECIMAL,
    PRIMARY KEY (`id_b`)
);

CREATE TABLE `test_transform_c`(
    `id_c` INTEGER NOT NULL AUTO_INCREMENT,
    `s1_c` VARCHAR(50) NULL,
    `s2_c` VARCHAR(255) NULL,
    `longstring_c` MEDIUMTEXT NULL,
    `time_c` DATETIME,
    `date_c` DATE,
    `boolean_c` BIT,
    `bigint_c` BIGINT,
    `decimal_c` DECIMAL,
    PRIMARY KEY (`id_c`)
);

CREATE TABLE `test_transform_d`(
    `id_d` INTEGER NOT NULL AUTO_INCREMENT,
    `s1_d` VARCHAR(50) NULL,
    `s2_d` VARCHAR(255) NULL,
    `longstring_d` MEDIUMTEXT NULL,
    `time_d` DATETIME,
    `date_d` DATE,
    `boolean_d` BIT,
    `bigint_d` BIGINT,
    `decimal_d` DECIMAL,
    PRIMARY KEY (`id_d`)
);

CREATE TABLE `test_a`(
    `id` VARCHAR(10) NOT NULL,
    PRIMARY KEY (`id`)
);

CREATE TABLE `test_b`(
    `id` VARCHAR(10) NOT NULL,
    `aid` VARCHAR(10) NOT NULL,
    PRIMARY KEY (`id`)
);
ALTER TABLE `test_b`
    ADD CONSTRAINT `fk_b_to_a_id` FOREIGN KEY (`aid`) REFERENCES `test_a` (`id`);
