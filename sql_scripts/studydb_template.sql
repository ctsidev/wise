-- WISE-only template for schema (no LOFTS or random_assignment table)
-- Based on dump originally from Database: drew_new
-- ------------------------------------------------------
-- Server version	5.0.16-standard-log

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;


--
-- Table structure for table `consent_response`
--

DROP TABLE IF EXISTS `consent_response`;
CREATE TABLE `consent_response` (
  `invitee` int(6) default NULL,
  `answer` char(1) default NULL,
  `viewdate` timestamp NOT NULL default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP,
  `survey` varchar(64) NOT NULL default '',
  KEY `invitee` (`invitee`),
  CONSTRAINT `consent_response_ibfk_1` FOREIGN KEY (`invitee`) REFERENCES `invitee` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table `decline_hits`
--

DROP TABLE IF EXISTS `decline_hits`;
CREATE TABLE `decline_hits` (
  `msg_id` int(6) default NULL,
  `survey` varchar(64) default NULL,
  `viewdate` timestamp NOT NULL default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP,
  KEY `msg_id` (`msg_id`),
  CONSTRAINT `decline_hits_ibfk_1` FOREIGN KEY (`msg_id`) REFERENCES `survey_message_use` (`messageId`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table `decline_reason`
--

DROP TABLE IF EXISTS `decline_reason`;
CREATE TABLE `decline_reason` (
  `invitee` int(6) default NULL,
  `reason` text,
  `declinedate` timestamp NOT NULL default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP,
  KEY `invitee` (`invitee`),
  CONSTRAINT `decline_reason_ibfk_1` FOREIGN KEY (`invitee`) REFERENCES `invitee` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table `interview_assignment`
--

DROP TABLE IF EXISTS `interview_assignment`;
CREATE TABLE `interview_assignment` (
  `id` int(6) NOT NULL auto_increment,
  `interviewer` int(6) default NULL,
  `invitee` int(6) default NULL,
  `survey` varchar(64) default NULL,
  `close_date` timestamp NOT NULL default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP,
  `assign_date` timestamp NOT NULL default '0000-00-00 00:00:00',
  `pending` int(1) default '1',
  PRIMARY KEY  (`id`),
  KEY `interviewer` (`interviewer`,`invitee`,`survey`),
  CONSTRAINT `interview_assignment_ibfk_1` FOREIGN KEY (`interviewer`) REFERENCES `interviewer` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table `interview_session`
--

DROP TABLE IF EXISTS `interview_session`;
CREATE TABLE `interview_session` (
  `session_id` int(6) NOT NULL default '0',
  `assign_id` int(6) default NULL,
  PRIMARY KEY  (`session_id`),
  KEY `assign_id` (`assign_id`),
  CONSTRAINT `interview_session_ibfk_1` FOREIGN KEY (`session_id`) REFERENCES `survey_user_session` (`id`) ON DELETE CASCADE,
  CONSTRAINT `interview_session_ibfk_2` FOREIGN KEY (`assign_id`) REFERENCES `interview_assignment` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table `interviewer`
--

DROP TABLE IF EXISTS `interviewer`;
CREATE TABLE `interviewer` (
  `id` int(6) NOT NULL auto_increment,
  `username` varchar(64) default NULL,
  `firstname` varchar(64) default NULL,
  `lastname` varchar(64) default NULL,
  `salutation` varchar(5) default NULL,
  `email` varchar(64) default NULL,
  `submittime` timestamp NOT NULL default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP,
  PRIMARY KEY  (`id`),
  KEY `id` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table `invitee`
--

DROP TABLE IF EXISTS `invitee`;
CREATE TABLE `invitee` (
  `id` int(6) NOT NULL auto_increment,
  `firstname` varchar(64) default NULL,
  `lastname` varchar(64) default NULL,
  `salutation` varchar(5) default NULL,
  `email` varchar(64) default NULL,
  `phone` varchar(12) default NULL,
  `irb_id` varchar(11) default NULL,
  `subj_type` char(1) default NULL,
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table `page_submit`
--

DROP TABLE IF EXISTS `page_submit`;
CREATE TABLE `page_submit` (
  `invitee` int(6) default NULL,
  `survey` varchar(64) default NULL,
  `page` varchar(64) default NULL,
  `Created` timestamp NOT NULL default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP,
  KEY `invitee` (`invitee`),
  CONSTRAINT `page_submit_ibfk_1` FOREIGN KEY (`invitee`) REFERENCES `invitee` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table `page_view`
--

DROP TABLE IF EXISTS `page_view`;
CREATE TABLE `page_view` (
  `invitee` int(8) NOT NULL default '0',
  `page` varchar(64) default NULL,
  `time` varchar(8) default NULL,
  `idle` varchar(8) default NULL,
  `hittime` timestamp NOT NULL default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP,
  KEY `invitee` (`invitee`),
  CONSTRAINT `page_view_ibfk_1` FOREIGN KEY (`invitee`) REFERENCES `invitee` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table `pending`
--

DROP TABLE IF EXISTS `pending`;
CREATE TABLE `pending` (
  `invitee` int(8) NOT NULL,
  `send_time` datetime default NULL,
  `message` varchar(32) default NULL,
  `survey` varchar(32) default NULL,
  `completed` char(1) default NULL,
  `completed_time` datetime default NULL,
  KEY `invitee` (`invitee`),
  CONSTRAINT `pending_ibfk_1` FOREIGN KEY (`invitee`) REFERENCES `invitee` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;


--
-- Table structure for table `survey_message_use`
--

DROP TABLE IF EXISTS `survey_message_use`;
CREATE TABLE `survey_message_use` (
  `id` int(6) NOT NULL auto_increment,
  `invitee` int(6) default NULL,
  `survey` varchar(64) default NULL,
  `message` varchar(64) default NULL,
  `sent_date` timestamp NOT NULL default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP,
  PRIMARY KEY  (`id`),
  KEY `invitee` (`invitee`),
  CONSTRAINT `survey_message_use_ibfk_1` FOREIGN KEY (`invitee`) REFERENCES `invitee` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table `survey_user_session`
--

DROP TABLE IF EXISTS `survey_user_session`;
CREATE TABLE `survey_user_session` (
  `id` int(6) NOT NULL auto_increment,
  `endtime` timestamp NOT NULL default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP,
  `starttime` timestamp NOT NULL default '0000-00-00 00:00:00',
  `from_message` int(6) NOT NULL default '0',
  `browser_info` text NOT NULL,
  PRIMARY KEY  (`id`),
  KEY `from_message` (`from_message`),
  CONSTRAINT `survey_user_session_ibfk_1` FOREIGN KEY (`from_message`) REFERENCES `survey_message_use` (`messageId`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table `survey_user_state`
--

DROP TABLE IF EXISTS `survey_user_state`;
CREATE TABLE `survey_user_state` (
  `invitee` int(6) NOT NULL,
  `survey` varchar(64) NOT NULL,
  `message_sequence` varchar(64) default NULL,
  `state` varchar(64) default NULL,
  `state_count` int(3) NOT NULL default '1',
  `entry_time` timestamp NOT NULL default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP,
  PRIMARY KEY  (`invitee`, `survey`),
  KEY `invitee` (`invitee`),
  CONSTRAINT `survey_user_state_ibfk_1` FOREIGN KEY (`invitee`) REFERENCES `invitee` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

DROP TABLE IF EXISTS `survey_health`;
CREATE TABLE `survey_health` ( 
	`survey_name` varchar(32) NOT NULL, 
	`last_update_time` bigint(64) NOT NULL, 
	PRIMARY KEY (`survey_name`)
)ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table `surveys`
--

DROP TABLE IF EXISTS `surveys`;
CREATE TABLE `surveys` (
  `internal_id` int(6) NOT NULL auto_increment,
  `id` varchar(64) default NULL,
  `filename` varchar(64) default NULL,
  `title` varchar(255) default NULL,
  `uploaded` timestamp NOT NULL default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP,
  `status` char(1) default NULL,
  `archive_date` varchar(64) default NULL,
  `create_syntax` text,
  PRIMARY KEY  (`internal_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table `update_trail`
--

DROP TABLE IF EXISTS `update_trail`;
CREATE TABLE `update_trail` (
  `invitee` int(6) default NULL,
  `survey` varchar(64) default NULL,
  `page` varchar(64) default NULL,
  `ColumnName` varchar(32) default NULL,
  `OldValue` varchar(255) default NULL,
  `Modified` timestamp NOT NULL default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP,
  `CurrentValue` varchar(255) default NULL,
  KEY `invitee` (`invitee`),
  CONSTRAINT `update_trail_ibfk_1` FOREIGN KEY (`invitee`) REFERENCES `invitee` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Table structure for table `welcome_hits`
--

DROP TABLE IF EXISTS `welcome_hits`;
CREATE TABLE `welcome_hits` (
  `invitee` int(6) default NULL,
  `viewdate` timestamp NOT NULL default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP,
  `survey` varchar(64) NOT NULL default '',
  KEY `invitee` (`invitee`),
  CONSTRAINT `welcome_hits_ibfk_1` FOREIGN KEY (`invitee`) REFERENCES `invitee` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

