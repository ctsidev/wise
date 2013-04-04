package edu.ucla.wise.commons;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import org.apache.log4j.Logger;

import com.google.common.base.Strings;

import edu.ucla.wise.commons.InviteeMetadata.Values;
import edu.ucla.wise.commons.User.INVITEE_FIELDS;
import edu.ucla.wise.initializer.StudySpaceParametersProvider;
import edu.ucla.wise.studyspace.parameters.StudySpaceParameters;

/**
 * This class encapsulates the database interface for a Study Space. The static
 * part represents the MySQL interface in general
 * 
 * Also provides group-level update of the valid survey_user_states: invited,
 * declined, start_reminder_x, non_responder, started, interrupted,
 * completion_reminder_x, incompleter TODO: (low) abstract valid User-state
 * progression into static final strings, either in Data_Bank or User class
 */

public class Data_Bank {

    public static String mysql_server;
    public static final String db_driver = "jdbc:mysql://";
    public static final String MainTableExtension = "_data";

    public static final char intValueTypeFlag = 'n';
    public static final char textValueTypeFlag = 'a';
    public static final char decimalValueTypeFlag = 'd';

    public static final String intFieldDDL = " int(6),";
    public static final String textFieldDDL = " text,";
    public static final String decimalFieldDDL = " decimal(11,3),";
    // TODO (med) add mechanism to use decimalPlaces and maxSize rather than
    // this default

    private static Logger log = Logger.getLogger(Data_Bank.class);

    /** Instance Variables */

    Study_Space study_space;
    public String dbdata;
    public String dbuser;
    public String dbpwd;
    public String email_encryption_key;

    static void SetupDB(ResourceBundle props) {
	mysql_server = props.getString("mysql.server");
	try {
	    DriverManager.registerDriver(new com.mysql.jdbc.Driver());
	} catch (Exception e) {
	    log.error("Data_Bank init Error: " + e, e);
	}
    }

    /** constructor setting up data storage for a survey session */

    public Data_Bank(Study_Space ss) {
	study_space = ss;

	StudySpaceParameters params = StudySpaceParametersProvider
		.getInstance().getStudySpaceParameters(ss.study_name);
	dbuser = params.getDatabaseUsername();
	dbdata = params.getDatabaseName();
	//20dec dbuser = WISE_Application.sharedProps.getString(ss.study_name
	//	+ ".dbuser");
	//20dec dbdata = WISE_Application.sharedProps.getString(ss.study_name
	//	+ ".dbname");
	dbpwd = params.getDatabasePassword();
	// 20dec dbpwd = WISE_Application.sharedProps.getString(ss.study_name
	//	+ ".dbpass");
	email_encryption_key = params.getDatabaseEncryptionKey();
	//email_encryption_key = WISE_Application.sharedProps
	//	.getString(ss.study_name + ".dbCryptKey");
    }

    // get list of current survey xml files and request loading of each by
    // study_space
    public void readSurveys() {
	try {
	    Connection conn = getDBConnection();
	    Statement stmt = conn.createStatement();
	    // Read all the surveys in the current database
	    String sql = "SELECT filename from surveys, "
		    + "(SELECT max(internal_id) as maxint FROM surveys group by id) maxes "
		    + "WHERE maxes.maxint = surveys.internal_id";
	    stmt.execute(sql);
	    ResultSet rs = stmt.getResultSet();
	    while (rs.next()) {
		String filename = rs.getString("filename");
		study_space.load_survey(filename);
	    }
	    // close database
	    stmt.close();
	    conn.close();
	} catch (Exception e) {
	    log.error(
		    "Data_Bank user creation error:" + e.toString(), e);
	}
    }

    // return a string vector with UserID, SurveyID
    public User makeUser_fromMsgID(String msg_id) {
	User theUser = null;
	try {
	    Connection conn = getDBConnection();
	    Statement stmt = conn.createStatement();
	    String usrID, surveyID;
	    Survey survey;
	    // get the user's ID and the survey ID being responded to
	    String sql = "select invitee, survey from survey_message_use where messageId='"
		    + msg_id + "'";
	    stmt.execute(sql);
	    ResultSet rs = stmt.getResultSet();
	    if (rs.next()) // if message id not found, result set will be empty
	    {
		usrID = rs.getString("invitee");
		surveyID = rs.getString("survey");
		survey = getSurvey(surveyID);
		System.out.println(surveyID);
		if (usrID == null || survey == null) {
		    throw new Exception("Can't get user " + usrID
			    + " or survey ID " + surveyID);
		}
		theUser = new User(usrID, survey, msg_id, this);
	    }
	    rs.close();
	    stmt.close();
	    conn.close();
	} catch (Exception e) {
	    log.error(
		    "Data_Bank user creation error:" + e.toString(), e);
	}
	return theUser;
    }

    /** establish dbase connection and returns a Connection object */
    public Connection getDBConnection() throws SQLException {
	return DriverManager.getConnection(db_driver + mysql_server + "/"
		+ dbdata + "?user=" + dbuser + "&password=" + dbpwd
		+ "&autoReconnect=true");
    }

    public Survey getSurvey(String sid) {
	return study_space.get_Survey(sid);
    }

    // SURVEY DDL & UPDATING FUNCTIONS

    public void setup_survey(Survey survey) throws SQLException {
	// Pralav- first handle repeating questions
	ArrayList<Repeating_Item_Set> repeating_item_sets = survey
		.get_repeating_item_sets();

	for (Repeating_Item_Set repeat_set_instance : repeating_item_sets) {
	    // generate a table for this instance
	    create_repeating_set_table(repeat_set_instance);
	}

	// "create_string" contains just the core syntax representing the survey
	// fields; can test for changes by comparing this
	String new_create_str = "";// old_create_str;
	String[] fieldList = survey.get_fieldList();

	char[] valTypeList = survey.get_valueTypeList();
	for (int i = 0; i < fieldList.length; i++){
	    if(fieldList[i]!=null){
		if (valTypeList[i] == textValueTypeFlag)
		    new_create_str += fieldList[i] + textFieldDDL;
		else if (valTypeList[i] == decimalValueTypeFlag)
		    new_create_str += fieldList[i] + decimalFieldDDL;
		else
		    new_create_str += fieldList[i] + intFieldDDL;
	    }

	    // DON'T chop trailing comma as it precedes rest of DDL string:
	    // WISE_Application.email_alert("DataBank create string:"+new_create_str,
	    // null);
	}


	String create_sql = "";
	try {
	    Connection conn = getDBConnection();
	    Statement stmt = conn.createStatement();
	    Statement stmt_m = conn.createStatement();

	    /*
	     * //if all columns are the same, then keep the old table if( -- OLD
	     * FIELDS SAME AS NEW FIELDS -- ) { //clean up the value of archive
	     * date in table of surveys survey.update_archive_date(conn);
	     * //update the creation syntax for the new record String sql =
	     * "select internal_id, uploaded, archive_date from surveys where internal_id=(select max(internal_id) from surveys where id='"
	     * +id+"')"; stmt.execute(sql); ResultSet rs = stmt.getResultSet();
	     * //keep the uploaded value - (mysql tends to wipe it off by using
	     * the current timestamp value) //and set the archive date to be
	     * current - (it's the current survey, has not been archived yet)
	     * if(rs.next()) { String
	     * sql_m="update surveys set create_syntax='"+
	     * new_create_str+"', uploaded='"
	     * +rs.getString(2)+"', archive_date='current' where internal_id="
	     * +rs.getString(1); boolean dbtype_m = stmt_m.execute(sql_m); }
	     * 
	     * return; //leave the old data table and other relevant tables
	     * alone } else
	     */
	    // get the temporary survey record inserted by admin tool in the
	    // SURVEYS table
	    String sql = "select internal_id, filename, title, uploaded, status "
		    + "from surveys where internal_id=(select max(internal_id) from surveys where id='"
		    + survey.id + "')";
	    stmt.execute(sql);
	    ResultSet rs = stmt.getResultSet();
	    String internal_id, filename, title, uploaded, status;
	    
	    if (rs.next()) {
		// save the data of the newly inserted survey record
		internal_id = rs.getString("internal_id");
		filename = rs.getString("filename");
		title = rs.getString("title");
		uploaded = rs.getString("uploaded");
		status = rs.getString("status");

		// delete the newly inserted survey record
		String sql_m = "delete from surveys where internal_id="
			+ internal_id;
		stmt_m.execute(sql_m);

		// archive the old data table if it exists in the database
		String old_archive_date = archive_table(survey);

		// create new data table
		create_sql = "CREATE TABLE " + survey.id + MainTableExtension
			+ " (invitee int(6) not null, status varchar(64),";
		create_sql += new_create_str;
		create_sql += "PRIMARY KEY (invitee),";
		create_sql += "FOREIGN KEY (invitee) REFERENCES invitee(id) ON DELETE CASCADE";
		create_sql += ") ";

		log.info("Create table statement is:" + sql_m);

		stmt_m.execute(create_sql);

		// add the new survey record back in the table of surveys, and
		// save the new table creation syntax
		// and set the archive date to be current - (it's the current
		// survey, has not been archived yet)
		sql_m = "insert into surveys(internal_id, id, filename, title, uploaded, status, archive_date, create_syntax) "
			+ "values("
			+ internal_id
			+ ",'"
			+ survey.id
			+ "','"
			+ filename
			+ "',\""
			+ title
			+ "\",'"
			+ uploaded
			+ "','"
			+ status + "','current','" + new_create_str + "')";
		stmt_m.execute(sql_m);

		// append the data from the old data table to the new created
		// one
		// if in production mode, status.equalsIgnoreCase("P") but
		// taking that out of criteria for user trust
		// if(old_archive_date!=null &&
		// !old_archive_date.equalsIgnoreCase("") &&
		// !old_archive_date.equalsIgnoreCase("no_archive") )
		if (old_archive_date != null
			&& !old_archive_date.equalsIgnoreCase(""))
		    append_data(survey, old_archive_date);

	    } // end of if
	    stmt.close();
	    stmt_m.close();
	} catch (Exception e) {
	    log.error(
"SURVEY - CREATE TABLE: " + create_sql, e);
	}
	return;
    }

    public void create_repeating_set_table(Repeating_Item_Set i_repeating_set) {

	String table_name = i_repeating_set.get_name_for_repeating_set();

	archiveTable("repeat_set_" + table_name);

	String sql_field_list = "";//
	String[] fieldList = i_repeating_set.listFieldNames();

	char[] valTypeList = i_repeating_set.getValueTypeList();
	for (int i = 0; i < fieldList.length; i++) {
	    if (valTypeList[i] == textValueTypeFlag)
		sql_field_list += fieldList[i] + textFieldDDL;
	    else if (valTypeList[i] == decimalValueTypeFlag)
		sql_field_list += fieldList[i] + decimalFieldDDL;
	    else
		sql_field_list += fieldList[i] + intFieldDDL;
	}

	try {
	    Connection conn = getDBConnection();
	    Statement stmt = conn.createStatement();

	    String sql_statement = "";
	    // create new data table
	    sql_statement = "CREATE TABLE "
		    + "repeat_set_"
		    + table_name
		    + " (instance int(6) not null auto_increment, invitee int(6) not null, instance_name text null, ";
	    sql_statement += sql_field_list;
	    sql_statement += "PRIMARY KEY (instance),";
	    sql_statement += "FOREIGN KEY (invitee) REFERENCES invitee(id) ON DELETE CASCADE";
	    sql_statement += ")";
	    stmt.execute(sql_statement);

	} catch (Exception e) {
	    WISE_Application.log_error(
		    "Repeating Set - CREATE TABLE: " + e.toString(), null);
	}
	return;

    }

    /** update the value of archive date in table of surveys */
    public void update_archive_date(Survey survey) {
	try {
	    Connection conn = getDBConnection();
	    Statement stmt = conn.createStatement();
	    Statement stmt_m = conn.createStatement();
	    Statement stmt_n = conn.createStatement();

	    // get the internal id of the old survey record
	    String sql = "select max(internal_id) from "
		    + "(select * from surveys where id='" + survey.id
		    + "' and internal_id <> "
		    + "(select max(internal_id) from surveys where id='"
		    + survey.id + "')) as a group by a.id;";
	    stmt.execute(sql);
	    ResultSet rs = stmt.getResultSet();
	    // get the uploaded date
	    if (rs.next()) {
		String sql_m = "select internal_id, uploaded from surveys where internal_id="
			+ rs.getString(1);
		stmt_m.execute(sql_m);
		ResultSet rs_m = stmt_m.getResultSet();
		// keep the value of uploaded date - (mysql tends to
		// automatically update this value
		// and set the archive date to be none - (no need to do the
		// archive since data sets are identical)
		if (rs_m.next()) {
		    String sql_n = "update surveys set uploaded='"
			    + rs_m.getString(2)
			    + "', archive_date='' where internal_id="
			    + rs_m.getString(1);
		    stmt_n.execute(sql_n);
		}
	    }
	    stmt.close();
	    stmt_m.close();
	    stmt_n.close();

	} catch (Exception e) {
	    WISE_Application.log_error(
		    "SURVEY - UPDATE ARCHIVE DATE: " + e.toString(), null);
	}
	return;
    }

    public String archiveTable(String tableName){
	String archiveString = "";
	try{
	    Connection connection = getDBConnection();
	    Statement statement = connection.createStatement();

	    boolean oldTableFound = false;
	    String archiveDate = "";

	    ResultSet resultSet = statement.executeQuery("show tables");

	    while(resultSet.next()){
		if(resultSet.getString(1).equalsIgnoreCase(tableName)){
		    oldTableFound = true;
		    break;
		}
	    }

	    if(oldTableFound){
		String sqlToCheckIfTableIsEmpty = "select * from "+tableName;
		Statement statementToCheckEmpty = connection.createStatement();
		ResultSet resultSetForTable = statementToCheckEmpty.executeQuery(sqlToCheckIfTableIsEmpty);

		if (!resultSetForTable.next()) {
		    String sqlToDropTable = "DROP TABLE IF EXISTS " + tableName;
		    statementToCheckEmpty.execute(sqlToDropTable);
		    // return empty archive date
		    archiveDate = "";

		} else {
		    // otherwise, archive the table by changing its name with
		    // the current timestamp
		    // get the current date
		    java.util.Date today = new java.util.Date();
		    SimpleDateFormat formatter = new SimpleDateFormat(
			    "yyyyMMddhhmm");
		    archiveDate = formatter.format(today);

		    String sqlToAlterTable = "ALTER TABLE " + tableName + " RENAME " + tableName
			    + "_arch_" + archiveDate;
		    statement.execute(sqlToAlterTable);

		    archiveString = archiveDate;
		}

	    }
	} catch(SQLException e){
	    WISE_Application.log_error("Error while archiving survey", e);
	}
	return archiveString;
    }

    /**
     * archive the old data table -- called both for D and P mode if new survey
     * uploaded or if P survey closed
     */
    public String archive_table(Survey survey) {
	String archive_str = "";
	String archive_date = "";
	try {
	    Connection conn = getDBConnection();
	    Statement stmt = conn.createStatement();
	    Statement stmt_m = conn.createStatement();
	    // check if the old data table exists in the current database
	    boolean found = false;
	    ResultSet rs = stmt.executeQuery("show tables");
	    while (rs.next()) {
		if (rs.getString(1).equalsIgnoreCase(
			survey.id + MainTableExtension)) {
		    found = true;
		    break;
		}
	    }
	    // if the old data table can be found
	    if (found) {
		// then check if the table is empty
		String sql_m = "select * from " + survey.id
			+ MainTableExtension;
		stmt_m.execute(sql_m);
		ResultSet rs_m = stmt_m.getResultSet();
		// if the table is empty, simply drop the table - no need to
		// archive
		if (!rs_m.next()) {
		    String sql = "DROP TABLE IF EXISTS " + survey.id
			    + MainTableExtension;
		    stmt.execute(sql);
		    // return empty archive date
		    archive_date = "";

		} else {
		    // otherwise, archive the table by changing its name with
		    // the current timestamp
		    // get the current date
		    java.util.Date today = new java.util.Date();
		    SimpleDateFormat formatter = new SimpleDateFormat(
			    "yyyyMMddhhmm");
		    archive_date = formatter.format(today);

		    String sql = "ALTER TABLE " + survey.id
			    + MainTableExtension + " RENAME " + survey.id
			    + "_arch_" + archive_date;
		    stmt.execute(sql);
		}

		// update the archive date of this old survey record in the
		// table of surveys
		// the old survey record should have the max internal id since
		// the new survey record has been deleted from the table
		sql_m = "select internal_id, uploaded from surveys where internal_id=(select max(internal_id) from surveys where id='"
			+ survey.id + "')";
		stmt_m.execute(sql_m);
		rs_m = stmt_m.getResultSet();
		if (rs_m.next()) {
		    String sql = "update surveys set uploaded='"
			    + rs_m.getString(2) + "', archive_date='"
			    + archive_date + "' where internal_id="
			    + rs_m.getString(1);
		    stmt.execute(sql);
		    archive_str = archive_date;
		}

	    } // end of if
	    stmt_m.close();
	    stmt.close();
	} catch (Exception e) {
	    WISE_Application.log_error(
		    "SURVEY - ARCHIVE DATA TABLE: " + e.toString(), null);
	}
	return archive_str;
    }

    /**
     * append the data in the same named column(s) from archived data table to
     * the newly created one
     */
    public void append_data(Survey survey, String archive_date)
	    throws SQLException {
	try {
	    Connection conn = getDBConnection();
	    Statement stmt = conn.createStatement();
	    // get old data set - the columns names from the archived table
	    String sql = "show columns from " + survey.id + "_arch_"
		    + archive_date;
	    stmt.execute(sql);
	    ResultSet rs = stmt.getResultSet();
	    List<String> old_columns = new ArrayList<String>();
	    while (rs.next()) {
		// put Field names into the old data set array list
		old_columns.add(rs.getString(1));
	    }

	    // get new data set - the columns names from new created table
	    sql = "show columns from " + survey.id + MainTableExtension;
	    stmt.execute(sql);
	    rs = stmt.getResultSet();
	    Set<String> new_columns = new HashSet<String>();
	    while (rs.next()) {
		// put Field names into the new data set array list
		new_columns.add(rs.getString(1).toUpperCase());
	    }

	    // sort the two array list
	    Collections.sort(old_columns);

	    int i;
	    // compare with the two array list
	    List<String> common_columns = new ArrayList<String>();
	    // and put the common columns into the common data set array list
	    for (String old_str : old_columns) {
		if (new_columns.contains(old_str.toUpperCase())
			&& !old_str.equalsIgnoreCase("status")
			&& !old_str.equalsIgnoreCase("invitee")) {
		    common_columns.add(old_str);
		}
	    }
	    // A better and efficient of finding common elements betweens two
	    // lists is above.. this is deprecated code.
	    // for (i = 0, j = 0; i < old_columns.size(); i++) {
	    // String old_str = (String) old_columns.get(i);
	    // while (j < new_columns.size()) {
	    // String new_str = (String) new_columns.get(j);
	    // // the common data set doesn't include the columns of status
	    // // & invitee
	    // if (old_str.compareToIgnoreCase(new_str) == 0
	    // && !old_str.equalsIgnoreCase("status")
	    // && !old_str.equalsIgnoreCase("invitee")) {
	    // common_columns.add(old_str);
	    // j++;
	    // break;
	    // } else if (old_str.compareToIgnoreCase(new_str) < 0) {
	    // break;
	    // } else if (old_str.compareToIgnoreCase(new_str) > 0) {
	    // j++;
	    // }
	    // } // end of while
	    // }

	    // append the data by using <insert...select...> query
	    sql = "insert into " + survey.id + MainTableExtension
		    + " (invitee, status,";
	    for (i = 0; i < common_columns.size(); i++) {
		sql += common_columns.get(i);
		if (i != (common_columns.size() - 1))
		    sql += ", ";
	    }
	    sql += ") select ";
	    sql += survey.id + "_arch_" + archive_date + ".invitee, "
		    + survey.id + "_arch_" + archive_date + ".status, ";
	    for (i = 0; i < common_columns.size(); i++) {
		sql += survey.id + "_arch_" + archive_date + ".";
		sql += common_columns.get(i);
		if (i != (common_columns.size() - 1))
		    sql += ", ";
	    }
	    sql += " from " + survey.id + "_arch_" + archive_date;
	    // Study_Util.email_alert("SURVEY - APPEND DATA debug: "+sql);
	    stmt.execute(sql);
	    stmt.close();

	} catch (Exception e) {
	    WISE_Application.log_error("SURVEY - APPEND DATA: " + e.toString(),
		    null);
	}
	return;
    }

    /** Remove a survey -- should only be called for Development mode surveys */
    /**
     * Drop data tables including the survey data table & subject set data
     * tables; update surveys table
     */
    public String delete_survey(Survey survey) {
	String useResult = "";
	try {
	    // connect to the database
	    Connection conn = getDBConnection();
	    Statement stmt = conn.createStatement();
	    Statement stmt_m = conn.createStatement();
	    // pick up all the related data tables
	    String sql_m = "show tables";
	    stmt_m.execute(sql_m);
	    ResultSet rs_m = stmt_m.getResultSet();
	    while (rs_m.next()) {
		String table_name = rs_m.getString(1);
		if (table_name.indexOf(survey.id + "_") != -1
			&& table_name.indexOf(MainTableExtension) != -1) {
		    // drop this table
		    String sql = "DROP TABLE IF EXISTS " + table_name;
		    stmt.execute(sql);
		}
	    }
	    stmt.close();
	    useResult = clear_surveyUseData(survey);
	    sql_m = "Update surveys set status='R', uploaded=uploaded, archive_date='no_archive' "
		    + "WHERE id ='" + survey.id + "'";
	    stmt_m.execute(sql_m);
	    stmt_m.close();
	    return "<p align=center>Survey " + survey.id
		    + " successfully dropped & old survey files archived.</p>"
		    + useResult;
	} catch (Exception e) {
	    WISE_Application.log_error(
		    "SURVEY - DROP Table error: " + e.toString(), e);
	    return "<p align=center>ERROR deleting survey " + survey.id
		    + ".</p>" + useResult
		    + "Please discuss with the WISE Administrator.</p>";
	}
    }

    /**
     * delete survey references - those related data tables while survey is in
     * production mode
     */
    public String archive_prodSurvey(Survey survey) {
	try {
	    String archive_date = archive_table(survey);

	    // change the survey mode from P to C in table surveys
	    // C - survey closed
	    Connection conn = getDBConnection();
	    Statement stmt = conn.createStatement();
	    String sql = "update surveys set status='C', uploaded=uploaded, archive_date='"
		    + archive_date + "' " + "WHERE id ='" + survey.id + "'";
	    stmt.execute(sql);

	    // remove the interview records from table - interview_assignment
	    sql = "DELETE FROM interview_assignment WHERE survey = '"
		    + survey.id + "' and pending=-1";
	    stmt.execute(sql);

	    // delete the survey data from *some* related tables -- not sure why
	    // necessary
	    // String sql = "DELETE FROM update_trail WHERE survey = '" +
	    // survey.id + "'";
	    // stmt.execute(sql);
	    // sql = "DELETE FROM page_submit WHERE survey = '" + survey.id +
	    // "'";
	    // stmt.execute(sql);

	    stmt.close();
	    return "<p align=center>Survey "
	    + survey.id
	    + " successfully closed archived. Discuss with WISE database Admin if you need access to old data.</p>";
	} catch (Exception e) {
	    WISE_Application.log_error("Error - Closing PRODUCTION SURVEY: "
		    + e.toString(), e);
	    return "<p align=center>ERROR Closing survey " + survey.id
		    + ".</p>"
		    + "Please discuss with the WISE Administrator.</p>";

	}
    }

    /**
     * clear data from data tables including the survey data table & subject set
     * data tables
     */
    public String clear_surveyData(Survey survey) {
	String useResult = "";
	try {
	    Connection conn = getDBConnection();
	    Statement stmt = conn.createStatement();
	    Statement stmt_m = conn.createStatement();
	    // pick up all the related data tables

	    ResultSet rs = stmt.executeQuery("show tables");
	    while (rs.next()) {
		String table_name = rs.getString(1);
		if (table_name.indexOf(survey.id + "_") != -1
			&& table_name.indexOf(MainTableExtension) != -1) {
		    // delete this table
		    String sql_m = "delete from " + table_name;
		    stmt_m.execute(sql_m);
		}
	    }
	    stmt_m.close();
	    stmt.close();
	    conn.close();
	    useResult = clear_surveyUseData(survey);
	    return "<p align=center>Submitted data for survey " + survey.id
		    + " successfully cleared from database.</p>" + useResult;
	} catch (Exception e) {
	    WISE_Application.log_error(
		    "Error clearing survey data : " + e.toString(), e);
	    return "<p align=center>ERROR clearing data for survey "
	    + survey.id + " from database.</p>" + useResult
	    + "Please discuss with the WISE Administrator.</p>";
	}
    }

    /**
     * delete associated "use" data for the survey -- should only be enabled in
     * Development mode
     */
    public String clear_surveyUseData(Survey survey) {
	try {
	    Connection conn = getDBConnection();
	    Statement stmt = conn.createStatement();
	    String sql = "DELETE FROM update_trail WHERE survey = '"
		    + survey.id + "'";
	    stmt.execute(sql);
	    sql = "DELETE FROM survey_message_use WHERE survey = '" + survey.id
		    + "'";
	    stmt.execute(sql);
	    /*
	     * delete above cascades to survey_user_session
	     * 
	     * //welcome hits let's keep for now: sql =
	     * "DELETE FROM welcome_hits WHERE survey = '" + survey.id + "'";
	     * stmt.execute(sql);
	     */
	    sql = "DELETE FROM consent_response WHERE survey = '" + survey.id
		    + "'";
	    stmt.execute(sql);
	    sql = "DELETE FROM survey_user_state WHERE survey = '" + survey.id
		    + "'";
	    stmt.execute(sql);
	    sql = "DELETE FROM page_submit WHERE survey = '" + survey.id + "'";
	    stmt.execute(sql);
	    sql = "DELETE FROM interview_assignment WHERE survey = \""
		    + survey.id + "\"";
	    stmt.execute(sql);
	    stmt.close();
	    conn.close();
	    return "<p align=center>Associated use data for survey "
	    + survey.id
	    + " successfully cleared "
	    + "(tables survey_user_state, survey_message_use, page_submit, update_trail, consent_response & for interviews).</p>";
	} catch (Exception e) {
	    WISE_Application.log_error(e.toString(), e);
	    return "<p align=center>ERROR clearing Associated use data for survey "
	    + survey.id
	    + " from "
	    + "(one or more of the tables "
	    + "survey_user_state, survey_message_use, page_submit, update_trail, consent_response).";
	}
    }

    // ITERATE over all study spaces and send email invitations to any subject
    // due for a reminder or pending for initial invite
    public String send_reminders() {
	Message_Sequence msg_seq = null;
	String selectSql = "", outputStr = "";
	String survey_id;
	String msID;
	Connection conn = null;
	try {
	    // connect to the database
	    // Move users in the "started" state more than 6 hrs to be
	    // "interrupted", regardless of survey
	    conn = getDBConnection();
	    selectSql = "UPDATE survey_user_state SET state='interrupted', state_count=1, entry_time=entry_time "
		    + "WHERE state='started' AND entry_time <= date_sub(now(), interval 6 hour)";
	    Statement outr_stmt = conn.createStatement();
	    outr_stmt.executeUpdate(selectSql);
	    outr_stmt.close();

	    // TODO: pending table needs separate handling -- doesn't belong in
	    // loop below but how to get survey id otherwise
	    // //0. send initial invitations to users due for one according to
	    // the pending table
	    outputStr += ("\nChecking for pending initial invitations");
	    // also Survey was not previously included in WHERE. Check: Could
	    // this obviate need for separate function??

	    // run the sql query to update the user group's states
	    invite_pending_users();
	    // send initial invitation & reminders by going through each survey
	    // - message_seq pair currently in use
	    String sql1 = "SELECT distinct survey, message_sequence FROM survey_user_state order by survey";
	    Statement svy_stmt = conn.createStatement();
	    svy_stmt.execute(sql1);
	    ResultSet rs_survey = svy_stmt.getResultSet();
	    while (rs_survey.next()) {
		survey_id = rs_survey.getString("survey");
		msID = rs_survey.getString("message_sequence");
		msg_seq = study_space.get_preface().get_message_sequence(msID);
		if (msg_seq == null) {
		    continue;
		}

		outputStr += "\n\nStart checks for survey_id=" + survey_id
			+ ", message sequence id=" + msID;

		// 1. send the start reminders
		outputStr += advanceReminders("start", msg_seq, survey_id, conn);

		outputStr += advanceReminders("completion", msg_seq, survey_id,
			conn);
	    }// end of while
	    svy_stmt.close();
	    outputStr += ("\nEnd emailing at "
		    + Calendar.getInstance().getTime().toString() + "\n");
	} catch (Exception e) {
	    outputStr += ("\nReminder generation ERROR! w/ select sql ("
		    + selectSql + "): " + e.toString());
	    e.printStackTrace();
	} finally {
	    try {
		conn.close();
	    } catch (SQLException e) {
	    }
	}
	return outputStr;
    }

    private String advanceReminders(String reminderType,
	    Message_Sequence msg_seq, String survey_id, Connection conn) {
	Reminder remMsg, priorMsg;
	int remCount;
	String selectSql = "", updateSql = "", outputStr = "", entryState, lastState;
	Message_Sender sender = new Message_Sender(msg_seq); // sets up
	// properly-authenticated
	// mail session
	if (reminderType.equals("start")) {
	    remMsg = msg_seq.get_start_reminder(0);
	    entryState = "invited";
	    lastState = "non_responder";
	    remCount = msg_seq.total_start_reminders();
	} else {
	    remMsg = msg_seq.get_completion_reminder(0);
	    entryState = "interrupted";
	    lastState = "incompleter";
	    remCount = msg_seq.total_completion_reminders();
	}
	if (remMsg == null)
	    return "No " + reminderType + " reminders\n";
	int max_count = 1; // max in 1st entry state is 1
	int entryTrigDays = remMsg.trigger_days;

	for (int i = 0; i < remCount; i++) {
	    int n = i + 1; // i represents 0-based index for current reminder; n
	    // represents the number that administrators see
	    outputStr += "\nChecking for those needing a new " + reminderType
		    + "_reminder " + n + " from entry state " + entryState;
	    selectSql = "SELECT id, AES_DECRYPT(email,\""
		    + email_encryption_key
		    + "\") as email, salutation, firstname, lastname "
		    + "FROM invitee, survey_user_state WHERE survey='"
		    + survey_id + "' AND state='" + entryState + "' "
		    + " AND entry_time <= date_sub(now(), interval "
		    + entryTrigDays + " day) " + " AND state_count >= "
		    + max_count + " AND id=invitee AND message_sequence='"
		    + msg_seq.id + "'";
	    updateSql = "UPDATE survey_user_state SET state='" + reminderType
		    + "_reminder_" + n + "', state_count=1 WHERE survey='"
		    + survey_id + "' AND invitee=";
	    outputStr += send_reminders(survey_id, sender, remMsg, selectSql,
		    updateSql, conn);

	    outputStr += ("\nChecking for those needing another "
		    + reminderType + " reminder " + n);
	    // Select users NOT at max
	    selectSql = "SELECT id, AES_DECRYPT(email,\""
		    + email_encryption_key
		    + "\") as email, salutation, firstname, lastname "
		    + "FROM invitee, survey_user_state WHERE state='"
		    + reminderType + "_reminder_" + n + "' AND survey='"
		    + survey_id + "'"
		    + " AND entry_time <= date_sub(now(), interval "
		    + remMsg.trigger_days + " day)" + " AND state_count < "
		    + remMsg.max_count
		    + " AND id=invitee AND message_sequence='" + msg_seq.id
		    + "'";
	    updateSql = "UPDATE survey_user_state SET state_count=state_count+1 "
		    + "WHERE survey='" + survey_id + "' AND invitee=";
	    outputStr += send_reminders(survey_id, sender, remMsg, selectSql,
		    updateSql, conn);
	    entryState = reminderType + "_reminder_" + n;
	    entryTrigDays = remMsg.trigger_days;
	    if (n < remCount) // need to keep last for final tag-out, below
	    {
		priorMsg = remMsg;
		if (reminderType.equals("start")) {
		    remMsg = msg_seq.get_start_reminder(n);
		} else {
		    remMsg = msg_seq.get_completion_reminder(n);
		}
	    }

	    // selectSql = "SELECT id, email, salutation, firstname, lastname "
	    // +
	    // "FROM invitee, survey_user_state WHERE state='"+reminderType+"_reminder_"+i+"' "
	    // +
	    // "AND survey='"+survey_id+"' AND state_count >= "+priorMsg.max_count+" "
	    // + "AND entry_time <= date_sub(now(), interval "+
	    // priorMsg.trigger_days + " day) " +
	    // "AND id=invitee AND message_sequence='"+msg_seq.id+"'";
	    // updateSql =
	    // "UPDATE survey_user_state SET state='"+reminderType+"_reminder_"
	    // +(i+1)+ "', state_count=1 "
	    // + "WHERE survey='"+survey_id+"' AND invitee=";
	    // outputStr+=send_reminders(survey_id, fromStr, remMsg, selectSql,
	    // updateSql, conn);

	}

	// Move users at max of last reminder to to final state
	selectSql = "UPDATE survey_user_state SET state='" + lastState
		+ "', state_count=1 " + "WHERE state='"
		+ reminderType
		+ "_reminder_"
		+ remCount
		+ "' " // same as entryState
		+ "AND state_count = " + remMsg.max_count + " "
		+ "AND entry_time <= date_sub(now(), interval "
		+ remMsg.trigger_days + " day) " + "AND survey='" + survey_id
		+ "' AND message_sequence='" + msg_seq.id + "'";
	// (No message to send; run UPDATE on all at once)
	try {
	    Statement statement = conn.createStatement();
	    statement.execute(selectSql);
	    statement.close();
	} catch (Exception e) {
	    outputStr += ("\nadvanceReminder ERROR! w/ select sql ("
		    + selectSql + "): " + e.toString());
	}
	return outputStr;
    }

    /**
     * This function will read and update the pending table, sending messages
     * that are due.
     * 
     * @param conn
     * @return
     */
    private String invite_pending_users() {

	String sql = "", outputStr = "";
	String selectSql = "SELECT id, AES_DECRYPT(email,'"
		+ this.study_space.db.email_encryption_key
		+ "') as email, salutation, firstname, lastname, survey, message_sequence FROM invitee, pending "
		+ "WHERE DATE(send_time) <= DATE(now()) AND pending.completed = 'N' AND invitee.id = pending.invitee";
	Statement statement = null;
	Connection conn = null;

	try {
	    conn = getDBConnection();
	    statement = conn.createStatement();
	    statement.execute(selectSql);
	    ResultSet rs = statement.getResultSet();
	    while (rs.next()) {
		String inviteeId = rs.getString("id");
		String email = rs.getString("email");
		String salutation = rs.getString("salutation");
		String lastname = rs.getString("lastname");
		String survey_id = rs.getString("survey");
		String msID = rs.getString("message_sequence");
		Message_Sequence msg_seq = study_space.get_preface()
			.get_message_sequence(msID);
		Message_Sender message_sender = new Message_Sender(msg_seq);

		Message invMsg = msg_seq.get_type_message("invite");
		if (invMsg == null) {
		    WISE_Application.log_error(
			    "Failed to get the initial invitation", null);
		    return "Failed";
		}

		outputStr += ("Sending invitation to invitee = " + inviteeId);
		Statement statement2 = conn.createStatement();

		String messageId = org.apache.commons.lang3.RandomStringUtils
			.randomAlphanumeric(22);

		sql = "INSERT INTO survey_message_use(messageId,invitee, survey, message) VALUES ('"
			+ messageId
			+ "',"
			+ inviteeId
			+ ",'"
			+ survey_id
			+ "', '"
			+ invMsg.id
			+ "')";
		statement2.execute(sql);
		String msg_index = "";
		if (invMsg.has_link) {
		    msg_index = messageId;
		}
		String email_response = message_sender.send_message(invMsg,
			msg_index, email, salutation, lastname, study_space.id);
		if (email_response.equalsIgnoreCase("")) {
		    outputStr += (" --> Email Sent");
		    // TODO: I have Fixed insertion of message_sequence in the
		    // reminder code, because it was not inserting any
		    // message_sequence in the survey_user_state table.
		    String updateSql = "INSERT INTO survey_user_state(invitee, survey, message_sequence, state, state_count) "
			    + "values ("
			    + inviteeId
			    + ", '"
			    + survey_id
			    + "', '"
			    + msID
			    + "','invited', 1) ON DUPLICATE KEY UPDATE state='invited', state_count=1";
		    statement2.execute(updateSql);
		    // update the pending table
		    String sql3 = "update pending set completed='Y', completed_time = now() where invitee="
			    + inviteeId
			    + " and survey ='"
			    + survey_id
			    + "' and message_sequence ='" + msID + "'";
		    statement2.execute(sql3);
		} else {
		    outputStr += (" --> ERROR SENDING EMAIL (" + email_response + ")");
		    WISE_Application.log_error(
			    "Error sending invitation email to invitee = "
				    + inviteeId, null);
		}// if
	    }// while
	} catch (Exception e) {
	    log.error("Pending initial invite ERROR:", e);
	} finally {
	    if (conn != null) {
		try {
		    conn.close();
		} catch (SQLException e) {
		}
	    }
	    if (statement != null) {
		try {
		    statement.close();
		} catch (SQLException e) {
		}
	    }
	}
	return outputStr;
    }

    private String send_reminders(String survey_id,
	    Message_Sender message_sender, Message r, String selQry,
	    String updQry, Connection conn) {
	String sql = "", outputStr = "";
	try {
	    Statement statement = conn.createStatement();
	    statement.execute(selQry);
	    ResultSet rs = statement.getResultSet();
	    while (rs.next()) {
		String iid = rs.getString("id");
		String email = rs.getString("email");
		String salutation = rs.getString("salutation");
		String lastname = rs.getString("lastname");
		outputStr += ("\nSending reminder invitation to invitee = " + iid);
		Statement statement2 = conn.createStatement();
		String messageId = org.apache.commons.lang3.RandomStringUtils
			.randomAlphanumeric(22);
		sql = "INSERT INTO survey_message_use(messageId,invitee, survey, message) VALUES ('"
			+ messageId
			+ "',"
			+ iid
			+ ",'"
			+ survey_id
			+ "', '"
			+ r.id + "')";
		statement2.execute(sql);
		String msg_index = "";
		if (r.has_link) {
		    msg_index = messageId;
		}
		// args: send_message(Message msg, String from_str, String
		// message_useID, String toEmail, String salutation, String
		// lastname, String ssid)
		String email_response = message_sender.send_message(r,
			msg_index, email, salutation, lastname, study_space.id);
		if (email_response.equalsIgnoreCase("")) {
		    outputStr += (" --> Email Sent");
		    statement2.execute(updQry + iid);
		} else {
		    outputStr += (" --> ERROR SENDING EMAIL (" + email_response + ")");
		    WISE_Application.log_error(
			    "Error sending invitation email to invitee = "
				    + iid, null);
		}
	    }// while
	} catch (Exception e) {
	    e.printStackTrace();
	    WISE_Application.log_error(
		    "Reminder sending ERROR: " + e.toString(), null);
	}
	return outputStr;
    }

    public Hashtable getData_forItem(String survey_id, String pgName,
	    String itemName, String whereclause) {
	Hashtable h1 = new Hashtable();
	try {
	    // connect to the database
	    Connection conn = getDBConnection();
	    Statement stmt = conn.createStatement();
	    // count the total number of invitees for each distinct answer;
	    // join to page_submit prevents counting nulls from people who never
	    // submitted the item
	    String sql = "select " + itemName
		    + ", count(distinct s.invitee) from " + survey_id
		    + MainTableExtension + " as s, page_submit as p where "
		    + "p.invitee=s.invitee and p.survey='" + survey_id + "'"
		    + " and p.page='" + pgName + "'";
	    if (!whereclause.equals(""))
		sql += " and s." + whereclause;
	    sql += " group by " + itemName;
	    stmt.execute(sql);
	    ResultSet rs = stmt.getResultSet();
	    while (rs.next()) {
		// if the answer is null
		if (rs.getString(1) == null)
		    h1.put("null", new Integer(rs.getInt(2)));
		else
		    h1.put(rs.getString(1), new Integer(rs.getInt(2)));
	    }
	    rs.close();
	    stmt.close();
	    conn.close();
	} catch (Exception e) {
	    WISE_Application.log_error(
		    "WISE - CLOSED QUESTION RENDER RESULTS EXCLUSIVE: "
			    + e.toString(), e);
	}
	return h1;
    }

    public HashMap<String, Float> getMinMax_forItem(Page page, String itemName,
	    String whereclause) {

	Connection conn = null;
	Statement stmt = null;
	ResultSet rs = null;
	String sql = null;
	HashMap<String, Float> retMap = new HashMap<String, Float>();

	try {
	    conn = getDBConnection();
	    stmt = conn.createStatement();
	    sql = "select "
		    + "min("
		    + itemName
		    + "), max("
		    + itemName
		    + ") from "
		    + page.survey.id
		    + "_data as s, page_submit as p where s.invitee=p.invitee and p.survey='"
		    + page.survey.id + "' and p.page='" + page.id + "'";
	    if (!whereclause.equalsIgnoreCase(""))
		sql += " and s." + whereclause;

	    stmt.execute(sql);
	    rs = stmt.getResultSet();
	    if (rs.next()) {
		retMap.put("min", rs.getFloat(1));
		retMap.put("max", rs.getFloat(2));
	    }
	} catch (SQLException ex) {
	    log.error("SQL Query Error", ex);
	    return retMap;
	} finally {
	    if (conn != null) {
		try {
		    conn.close();
		} catch (SQLException e) {
		    log.error(e);
		}
	    }
	    if (stmt != null) {
		try {
		    stmt.close();
		} catch (SQLException e) {
		    log.error(e);
		}
	    }
	    try {
		rs.close();
	    } catch (SQLException e) {
		log.error(e);
	    }
	}
	return retMap;

    }

    public HashMap<String, String> getHistogram_forItem(Page page,
	    String itemName, float scale_start, float bin_width_final,
	    String whereclause) {

	Connection conn = null;
	Statement stmt = null;
	ResultSet rs = null;
	String sql = null;
	HashMap<String, String> retMap = new HashMap<String, String>();

	try {
	    conn = getDBConnection();
	    stmt = conn.createStatement();
	    // get bins on that question from database
	    sql = "select floor(("
		    + itemName
		    + "-"
		    + scale_start
		    + ")/"
		    + bin_width_final
		    + "), count(*) from "
		    + page.survey.id
		    + "_data as s, page_submit as p where s.invitee=p.invitee and p.survey='"
		    + page.survey.id + "' and p.page='" + page.id + "'";

	    if (!whereclause.equalsIgnoreCase(""))
		sql += " and s." + whereclause;
	    sql += " group by floor((" + itemName + "-" + scale_start + ")/"
		    + bin_width_final + ")";
	    stmt.execute(sql);
	    rs = stmt.getResultSet();
	    String name, count;
	    while (rs.next()) {
		name = rs.getString(1);
		if (name == null)
		    name = "null";
		count = rs.getString(2);
		if (count == null) {
		    count = "null";
		    retMap.put("unanswered", String.valueOf(1));
		}

		retMap.put(name, count);
	    }

	} catch (SQLException ex) {
	    log.error("SQL Query Error", ex);
	    return retMap;
	} finally {
	    if (conn != null) {
		try {
		    conn.close();
		} catch (SQLException e) {
		    log.error(e);
		}
	    }
	    if (stmt != null) {
		try {
		    stmt.close();
		} catch (SQLException e) {
		    log.error(e);
		}
	    }
	    try {
		rs.close();
	    } catch (SQLException e) {
		log.error(e);
	    }
	}
	return retMap;

    }

    public void updateSurveyHealthStatus(String surveyName) {

	long currentTimeMillis = System.currentTimeMillis();
	StringBuffer query = new StringBuffer(
		"insert into survey_health (survey_name, last_update_time) values ('");
	query.append(surveyName).append("',").append(currentTimeMillis)
	.append(")");
	query.append(" on duplicate key update last_update_time=")
	.append(currentTimeMillis).append(";");
	Connection conn = null;
	Statement stmt = null;

	try {
	    conn = getDBConnection();
	    stmt = conn.createStatement();
	    stmt.executeUpdate(query.toString());
	} catch (SQLException e) {
	    log.error("Could not update survey_health table", e);
	} finally {
	    if (stmt != null) {
		try {
		    stmt.close();
		} catch (SQLException e) {
		    log.error(e);
		}
	    }
	    if (conn != null) {
		try {
		    conn.close();
		} catch (SQLException e) {
		    log.equals(e);
		}
	    }
	}

    }

    public long lastSurveyHealthUpdateTime(String studyName) {

	long lastUpdateTime = 0;
	StringBuffer query = new StringBuffer();
	query.append("select * from survey_health where survey_name='")
	.append(studyName).append("';");
	Connection conn = null;
	Statement stmt = null;

	try {
	    conn = getDBConnection();
	    stmt = conn.createStatement();
	    stmt.execute(query.toString());
	    ResultSet rs = stmt.getResultSet();
	    if (rs.next()) {
		lastUpdateTime = rs.getLong(2);
	    }

	} catch (SQLException e) {
	    log.error("Could not update survey_health table", e);
	} finally {
	    if (stmt != null) {
		try {
		    stmt.close();
		} catch (SQLException e) {
		    log.error(e);
		}
	    }
	    if (conn != null) {
		try {
		    conn.close();
		} catch (SQLException e) {
		    log.equals(e);
		}
	    }
	}
	return lastUpdateTime;

    }

    public String displayAddInvitee() {
	StringBuffer strBuff = new StringBuffer();
	Connection conn = null;
	Statement stmt = null;
	try {
	    // connect to the database
	    conn = getDBConnection();
	    stmt = conn.createStatement();
	    stmt.execute("describe invitee");
	    ResultSet rs = stmt.getResultSet();
	    while (rs.next()) {
		// read col name from database, matching col value from input
		// request
		String column_name = rs.getString("Field");
		if (column_name.equalsIgnoreCase("id"))
		    continue;
		strBuff.append("<tr><td width=400 align=left>").append(
			column_name);
		// check for required field values
		if (column_name.equalsIgnoreCase("lastname")) {
		    strBuff.append(" (required)");
		}
		strBuff.append(": <input type='text' name='")
		.append(column_name).append("' ");
		if (column_name.equalsIgnoreCase("salutation"))
		    strBuff.append("maxlength=5 size=5 ");
		else
		    strBuff.append("maxlength=64 size=40 ");
		strBuff.append("></td></tr>");
	    }
	    // display the submit button
	    strBuff.append("<tr><td align=center>")
	    .append("<input type='image' alt='submit' src='admin_images/submit.gif' border=0>")
	    .append("</td></tr>");

	} catch (SQLException e) {
	    e.printStackTrace();
	} finally {
	    if (conn != null) {
		try {
		    conn.close();
		} catch (SQLException e) {
		}
	    }
	    if (stmt != null) {
		try {
		    stmt.close();
		} catch (SQLException e) {
		}
	    }
	}
	return strBuff.toString();
    }

    public String displayAddInvitee(String surveyId) {

	Survey survey = study_space.get_Survey(surveyId);
	Map<String, Values> inviteeMap = new HashMap(
		survey.inviteeMetadata.fieldMap);
	StringBuffer strBuff = new StringBuffer();

	for (INVITEE_FIELDS field : INVITEE_FIELDS.values()) {
	    if (!field.isShouldDisplay()) {
		continue;
	    }
	    strBuff.append(getInviteeEntry(field.name(),
		    inviteeMap.get(field.name())));
	    inviteeMap.remove(field.name());
	}

	for (Map.Entry<String, Values> map : inviteeMap.entrySet()) {
	    strBuff.append(getInviteeEntry(map.getKey(), map.getValue()));
	}
	// display the submit button
	strBuff.append("<tr><td align=center>")
	.append("<input type='image' alt='submit' src='admin_images/submit.gif' border=0>")
	.append("</td></tr>");
	return strBuff.toString();
    }

    private String getInviteeEntry(String column_name, Values value) {
	StringBuffer strBuff = new StringBuffer();
	strBuff.append("<tr><td width=450 align=left>").append(value.label);
	// not a drop down
	if (value.values.size() == 0) {
	    strBuff.append(": <input type='text' name='").append(column_name)
	    .append("' ");
	    if (column_name.equalsIgnoreCase(INVITEE_FIELDS.salutation.name()))
		strBuff.append("maxlength=5 size=5 ");
	    else
		strBuff.append("maxlength=64 size=40 ");
	    strBuff.append("></td></tr>");
	} else {
	    strBuff.append(": <select name='").append(column_name).append("'>");
	    for (Map.Entry<String, String> valueNode : value.values.entrySet()) {
		strBuff.append("<option value='").append(valueNode.getKey())
		.append("'>");
		strBuff.append(valueNode.getValue() == null ? valueNode
			.getKey() : valueNode.getValue());
		strBuff.append("</option>");
	    }
	    strBuff.append("</select>");
	}
	return strBuff.toString();
    }

    public String addInviteeAndDisplayPage(Map<String, String> requestParameters) {
	return handle_addInvitees(requestParameters, true);
    }

    public int addInviteeAndReturnUserId(Map<String, String> requestParameters) {
	return Integer.parseInt(handle_addInvitees(requestParameters, false));
    }

    /** run database to handle input and also print table for adding invitees */
    private String handle_addInvitees(Map<String, String> requestParameters,
	    boolean showNextPage) {
	String errStr = "", resStr = "";
	int userId = 0;
	// connect to the database
	Connection conn = null;
	Statement stmt = null;
	try {
	    conn = getDBConnection();
	    stmt = conn.createStatement();
	    String sql, sql_ins = "insert into invitee(", sql_val = "values(";

	    // get the column names of the table of invitee
	    stmt.execute("describe invitee");
	    ResultSet rs = stmt.getResultSet();
	    boolean submit = (requestParameters.get("submit") != null);
	    while (rs.next()) {
		// read col name from database, matching col value from input
		// request
		String column_name = rs.getString("Field");
		if (column_name.equalsIgnoreCase("id"))
		    continue;
		
		String column_val = requestParameters.get(column_name);
		String column_type = rs.getString("Type");
		resStr += "<tr><td width=400 align=left>" + column_name;
		// check for required field values
		if (column_name.equalsIgnoreCase(User.INVITEE_FIELDS.lastname
			.name())
			|| (showNextPage && column_name
				.equalsIgnoreCase(User.INVITEE_FIELDS.email
					.name()))) {
		    resStr += " (required)";
		    if (submit && CommonUtils.isEmpty(column_val))
			errStr += "<b>" + column_name + "</b> ";
		}
		resStr += ": <input type='text' name='" + column_name + "' ";
		if (column_name.equalsIgnoreCase(User.INVITEE_FIELDS.salutation
			.name()))
		    resStr += "maxlength=5 size=5 ";
		else
		    resStr += "maxlength=64 size=40 ";
		if (submit) {
		    resStr += "value='" + column_val + "'"; // add submitted
		    sql_ins += column_name + ",";
		    if (column_name.equalsIgnoreCase(User.INVITEE_FIELDS.email
			    .name())) {
			if (CommonUtils.isEmpty(column_val)
				|| column_val.equalsIgnoreCase("null")) {
			    column_val = WISE_Application.alert_email;
			}
			sql_val += "AES_ENCRYPT('" + column_val + "','"
				+ email_encryption_key + "'),";
		    } else if (column_name
			    .equalsIgnoreCase(User.INVITEE_FIELDS.irb_id.name())) {
			sql_val += "\""
				+ (CommonUtils.isEmpty(column_val) ? ""
					: column_val) + "\",";
		    } else if (column_name
			    .equalsIgnoreCase(User.INVITEE_FIELDS.salutation
				    .name())) {
			sql_val += "\""
				+ (CommonUtils.isEmpty(column_val) ? "Mr."
					: column_val) + "\",";
		    } else {
			if (column_type.toLowerCase().contains("int")) {
			    sql_val += "\""
				    + (CommonUtils.isEmpty(column_val) ? "0"
					    : column_val) + "\",";
			} else {
			    sql_val += "\""
				    + (CommonUtils.isEmpty(column_val) ? ""
					    : column_val) + "\",";
			}
		    }
		}
		resStr += "></td></tr>";
	    }
	    // run the insertion if all the required fields have been filled in
	    // with values
	    if (!errStr.equals(""))
		resStr += "<tr><td align=center>Required fields " + errStr
			+ " not filled out </td></tr>";
	    else if (submit) {
		sql = sql_ins.substring(0, sql_ins.length() - 1) + ") "
			+ sql_val.substring(0, sql_val.length() - 1) + ")";
		stmt.execute(sql);
		resStr += "<tr><td align=center>New invitee "
			+ requestParameters.get("last_name")
			+ " has been added</td></tr>";
	    }
	    // display the submit button
	    resStr += "<tr><td align=center>"
		    + "<input type='hidden' name='submit' value='true' >"
		    + "<input type='image' alt='submit' src='admin_images/submit.gif' border=0>"
		    + "</td></tr>";
	    if (!showNextPage) {
		stmt.execute("select last_insert_id()");
		rs = stmt.getResultSet();
		if (rs.next()) {
		    userId = rs.getInt(1);
		}
	    }

	} catch (RuntimeException e) {
	    log.error("Runtime exception while loading invitee", e);
	} catch (SQLException e) {
	    log.error(
		    "WISE ADMIN - LOAD INVITEE: " + e.toString(), e);
	    resStr += "<p>Error: " + e.toString() + "</p>";
	    return resStr;
	} finally {
	    try {
		stmt.close();
	    } catch (SQLException e) {
	    }
	    try {
		conn.close();
	    } catch (SQLException e) {
	    }
	}
	return showNextPage ? resStr : String.valueOf(userId);
    }

    /*
     * Pralav public String addInviteeAndDisplayPage(HttpServletRequest request)
     * { return handle_addInvitees(request, true); }
     * 
     * public int addInviteeAndReturnUserId(HttpServletRequest request) { return
     * Integer.parseInt(handle_addInvitees(request, false)); }
     * 
     * /** run database to handle input and also print table for adding invitees
     */
    /*
     * Pralav private String handle_addInvitees(HttpServletRequest request,
     * boolean showNextPage) { String errStr = "", resStr = ""; int userId = 0;
     * // connect to the database Connection conn = null; Statement stmt = null;
     * try { conn = getDBConnection(); stmt = conn.createStatement(); String
     * sql, sql_ins = "insert into invitee(", sql_val = "values(";
     * 
     * // get the column names of the table of invitee
     * stmt.execute("describe invitee"); ResultSet rs = stmt.getResultSet();
     * boolean submit = (request.getParameter("submit") != null); while
     * (rs.next()) { // read col name from database, matching col value from
     * input // request String column_name = rs.getString("Field"); if
     * (column_name.equalsIgnoreCase("id")) continue; String column_val =
     * request.getParameter(column_name); String column_type =
     * rs.getString("Type"); resStr += "<tr><td width=400 align=left>" +
     * column_name; // check for required field values if
     * (column_name.equalsIgnoreCase(User.INVITEE_FIELDS.lastname .name()) ||
     * (showNextPage && column_name .equalsIgnoreCase(User.INVITEE_FIELDS.email
     * .name()))) { resStr += " (required)"; if (submit &&
     * CommonUtils.isEmpty(column_val)) errStr += "<b>" + column_name + "</b> ";
     * } resStr += ": <input type='text' name='" + column_name + "' "; if
     * (column_name.equalsIgnoreCase(User.INVITEE_FIELDS.salutation .name()))
     * resStr += "maxlength=5 size=5 "; else resStr += "maxlength=64 size=40 ";
     * if (submit) { resStr += "value='" + column_val + "'"; // add submitted
     * sql_ins += column_name + ","; if
     * (column_name.equalsIgnoreCase(User.INVITEE_FIELDS.email .name())) { if
     * (CommonUtils.isEmpty(column_val) || column_val.equalsIgnoreCase("null"))
     * { column_val = WISE_Application.alert_email; } sql_val += "AES_ENCRYPT('"
     * + column_val + "','" + email_encryption_key + "'),"; } else if
     * (column_name .equalsIgnoreCase(User.INVITEE_FIELDS.irb_id.name())) {
     * sql_val += "\"" + (CommonUtils.isEmpty(column_val) ? "" : column_val) +
     * "\","; } else if (column_name
     * .equalsIgnoreCase(User.INVITEE_FIELDS.salutation .name())) { sql_val +=
     * "\"" + (CommonUtils.isEmpty(column_val) ? "Mr." : column_val) + "\","; }
     * else { if (column_type.toLowerCase().contains("int")) { sql_val += "\"" +
     * (CommonUtils.isEmpty(column_val) ? "0" : column_val) + "\","; } else {
     * sql_val += "\"" + (CommonUtils.isEmpty(column_val) ? "" : column_val) +
     * "\","; } } } resStr += "></td></tr>"; } // run the insertion if all the
     * required fields have been filled in // with values if
     * (!errStr.equals("")) resStr += "<tr><td align=center>Required fields " +
     * errStr + " not filled out </td></tr>"; else if (submit) { sql =
     * sql_ins.substring(0, sql_ins.length() - 1) + ") " + sql_val.substring(0,
     * sql_val.length() - 1) + ")"; stmt.execute(sql); resStr +=
     * "<tr><td align=center>New invitee " + request.getParameter("lastname") +
     * " has been added</td></tr>"; } // display the submit button resStr +=
     * "<tr><td align=center>" +
     * "<input type='hidden' name='submit' value='true' >" +
     * "<input type='image' alt='submit' src='admin_images/submit.gif' border=0>"
     * + "</td></tr>"; if (!showNextPage) {
     * stmt.execute("select last_insert_id()"); rs = stmt.getResultSet(); if
     * (rs.next()) { userId = rs.getInt(1); } }
     * 
     * } catch (Exception e) { AdminInfo
     * .log_error("WISE ADMIN - LOAD INVITEE: " + e.toString(), e); resStr +=
     * "<p>Error: " + e.toString() + "</p>"; return resStr; } finally { try {
     * stmt.close(); } catch (SQLException e) { } try { conn.close(); } catch
     * (SQLException e) { } } return showNextPage ? resStr :
     * String.valueOf(userId); }
     */
    public String getCurrentSurveyIdString() {

	Connection conn = null;
	Statement stmt = null;
	String surveyIdString = null;

	try {
	    conn = getDBConnection();
	    stmt = conn.createStatement();

	    String sql = "select id from surveys where status in ('P', 'D') and internal_id in"
		    + "(select max(internal_id) from surveys group by id) order by uploaded DESC";
	    stmt.execute(sql);
	    ResultSet rs = stmt.getResultSet();
	    if (rs.next()) {
		surveyIdString = rs.getString(1);
	    }
	} catch (SQLException ex) {

	} finally {
	    try {
		stmt.close();
	    } catch (SQLException e) {
	    }
	    try {
		conn.close();
	    } catch (SQLException e) {
	    }
	}
	return surveyIdString;
    }

    // TODO: (low) continue moving functionality from User and Study_Space to
    // Data_Bank

    // If a new survey is uploaded and there is changes the invitee_fields, this
    // function will make sure that database and survey fields remain in sync.
    // This function ensures that it does not touches the default mandatory
    // arguments in the invitee table, even if they are tampered in the survey
    // XML.
    public void syncInviteeTable(InviteeMetadata inviteeMetadata) {
	Connection conn = null;
	Statement stmt = null;
	Set<String> dbColumns = new HashSet<String>();
	Set<String> columnsToBeRemoved = new HashSet<String>();
	Set<String> columnsToBeAdded = new HashSet<String>();
	try {
	    conn = getDBConnection();
	    stmt = conn.createStatement();
	    stmt.execute("describe invitee");
	    ResultSet rs = stmt.getResultSet();
	    while (rs.next()) {
		dbColumns.add(rs.getString("Field"));
	    }
	    for (Map.Entry<String, Values> map : inviteeMetadata.fieldMap
		    .entrySet()) {
		String columnName = map.getKey();
		Values columnValue = map.getValue();
		if (!columnValue.userNode || dbColumns.contains(columnName))
		    continue;
		columnsToBeAdded.add(columnName);
	    }
	    Iterator<String> it = dbColumns.iterator();
	    while (it.hasNext()) {
		String columnName = it.next();
		Values columnValue = inviteeMetadata.fieldMap.get(columnName);
		// present in the DB, but not in the Xml file.
		if (columnValue == null) {
		    if (INVITEE_FIELDS.contains(columnName))
			continue;
		    columnsToBeRemoved.add(columnName);
		}
	    }

	    it = columnsToBeAdded.iterator();
	    while (it.hasNext()) {
		String columnName = it.next();
		StringBuffer strBuff = new StringBuffer();
		strBuff.append("alter table invitee add column ")
		.append(columnName)
		.append("")
		.append(inviteeMetadata.fieldMap.get(columnName).type
			.substring(0, inviteeMetadata.fieldMap
				.get(columnName).type.length() - 1));
		stmt.execute(strBuff.toString());
	    }

	    it = columnsToBeRemoved.iterator();
	    while (it.hasNext()) {
		String columnName = it.next();
		StringBuffer strBuff = new StringBuffer();
		strBuff.append("alter table invitee drop column ").append(
			columnName);
		stmt.execute(strBuff.toString());
	    }
	} catch (SQLException e) {
	    e.printStackTrace();
	} finally {
	    if (conn != null) {
		try {
		    conn.close();
		} catch (SQLException e) {
		}
	    }
	    if (stmt != null) {
		try {
		    stmt.close();
		} catch (SQLException e) {
		}
	    }
	}
    }

    /*
     * public InputStream getFileFromDatabase(String cssFileName) { Connection
     * conn = null; PreparedStatement pstmnt = null; InputStream is = null;
     * 
     * try { conn = getDBConnection(); String querySQL =
     * "SELECT filecontents FROM wisefiles WHERE filename = '" + cssFileName +
     * "'"; pstmnt = conn.prepareStatement(querySQL); ResultSet rs =
     * pstmnt.executeQuery();
     * 
     * while (rs.next()) { is = rs.getBinaryStream(1); } } catch (SQLException
     * e) { e.printStackTrace();
     * log.error("Error while retrieving file from database"); } catch
     * (Exception e) { e.printStackTrace(); } finally { try { conn.close(); }
     * catch (SQLException e) { e.printStackTrace(); } } return is; }
     */

    public InputStream getFileFromDatabase(String fileName,
	    String studySpaceName) {
	Connection conn = null;
	PreparedStatement pstmnt = null;
	InputStream is = null;

	try {
	    conn = getDBConnection();
	    String querySQL = "SELECT filecontents FROM " + studySpaceName
		    + ".wisefiles WHERE filename = '" + fileName + "'";
	    pstmnt = conn.prepareStatement(querySQL);
	    ResultSet rs = pstmnt.executeQuery();

	    while (rs.next()) {
		is = rs.getBinaryStream(1);
	    }
	} catch (SQLException e) {
	    e.printStackTrace();
	    log.error("Error while retrieving file from database");
	} catch (Exception e) {
	    e.printStackTrace();
	} finally {
	    try {
		conn.close();
	    } catch (SQLException e) {
		e.printStackTrace();
	    }
	}
	return is;
    }

    public InputStream getXmlFileFromDatabase(String fileName,
	    String studySpaceName) {
	Connection connection = null;
	PreparedStatement prepStmt = null;
	InputStream inputStream = null;

	if (Strings.isNullOrEmpty(studySpaceName)) {
	    log.error("No study space name  provided");
	    return null;
	}

	try{
	    connection = getDBConnection();
	    String querySQL = "SELECT filecontents FROM "+studySpaceName+".xmlfiles WHERE filename='"+fileName+"'";
	    prepStmt = connection.prepareStatement(querySQL);
	    ResultSet resultSet = prepStmt.executeQuery();

	    while (resultSet.next()) {
		inputStream = resultSet.getBinaryStream(1);
	    }
	} catch (SQLException e) {
	    e.printStackTrace();
	    log.error("Error while retrieving file from database");
	}
	return inputStream;
    }

}