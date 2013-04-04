package edu.ucla.wise.commons;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;

import org.apache.log4j.Logger;

//Class User_DB_Connection -- a customized interface to encapsulate single-user interface to data storage
//TODO (low): consider getting rid of STATUS column in data table & just using page_submit; 
// nice thing tho is that STATUS is given back alongside the main data
public class User_DB_Connection {
    public User theUser = null;
    private final String surveyID;
    private final String mainTable_name;
    private Data_Bank db;
    private Connection conn = null;
    Logger log = Logger.getLogger(User_DB_Connection.class);

    /**
     * if there is a quote in the string, replace it with double quotes this is
     * necessary for sql to store the quote properly
     */
    public static String fixquotes(String s) {
	if (s == null)
	    return "";

	int len = s.length();
	String s1, s2;

	s2 = "";
	for (int i = 0; i < len; i++) {
	    s1 = s.substring(i, i + 1);
	    s2 = s2 + s1;
	    if (s1.equalsIgnoreCase("'"))
		s2 = s2 + "'";
	}
	return s2;
    }

    public User_DB_Connection(User usr, Data_Bank dbk) {
	theUser = usr;
	db = dbk;
	surveyID = usr.currentSurvey.id;
	mainTable_name = surveyID + Data_Bank.MainTableExtension;
	try {
	    conn = db.getDBConnection(); // open a database connection to hold
	    // for the user
	    // ultimately closed by finalize() below
	} catch (Exception e) {
	    WISE_Application.log_error(
		    "User " + theUser.id
			    + " unable to make its DB connection. Err: "
			    + e.toString(), null);
	}

    }

    // constructor version for testing only
    public User_DB_Connection(User user) {
	theUser = user;
	surveyID = user.currentSurvey.id;
	mainTable_name = surveyID + Data_Bank.MainTableExtension;
    }

    // finalize() called by garbage collector to clean up all objects
    @Override
    protected void finalize() throws Throwable {
	try {
	    conn.close();
	} catch (Exception e) {
	    WISE_Application.log_error("Exception for user " + theUser.id
		    + " closing DB connection w/: " + e.toString(), null);
	} finally {
	    super.finalize();
	}
    }

    /** retrieve values for a list of fields from the invitee table */
    public String[] getInviteeAttrs(String[] fieldNames) {
	
	HashSet<String> nonEncodedFieldSet = new HashSet<String>();
	nonEncodedFieldSet.add("firstname");
	nonEncodedFieldSet.add("lastname");
	nonEncodedFieldSet.add("salutation");
	nonEncodedFieldSet.add("phone");
	
	String userid = theUser.id;
	String[] values = new String[fieldNames.length];
	if (fieldNames.length < 1)
	    return values;
	String fieldString = "";
	for (int i = 0; i < fieldNames.length - 1; i++) {
	    fieldString += (!nonEncodedFieldSet.contains(fieldNames[i]
		    .toLowerCase()))
		    ? "AES_DECRYPT("
		    + User.INVITEE_FIELDS.email.name() + ",'"
		    + db.email_encryption_key + "')" : fieldNames[i];
	    fieldString += ",";
	}
	fieldString += fieldNames[fieldNames.length - 1];
	String sql = "SELECT " + fieldString + " FROM invitee WHERE id = "
		+ userid;
	try {
	    // connect to the database
	    Statement stmt = conn.createStatement();
	    // get the status' value from survey data table
	    stmt.execute(sql);
	    ResultSet rs = stmt.getResultSet();
	    // update the current page by searching with the status' value (page
	    // ID)
	    if (rs.next())
		for (int i = 0; i < fieldNames.length; i++)
		    values[i] = rs.getString(i + 1);
	    stmt.close();
	} catch (Exception e) {
	    WISE_Application.log_error(
		    "DataBank - Invitee attr retrieval fail: " + e.toString(),
		    null);
	    return null; // signal failure to retrieve
	}
	return values;
    }

    // Write array of values for a page and also the ID of next page to the
    // user's row in survey's main data table
    public int storeMainData(String[] names, char[] valTypes, String[] vals) {
	String sql = "", sqlu = "";
	String colNames = "", values = "", updateStr = "", updateTrailStr = "";
	// connect to the database
	Statement stmt = null;
	int n_toStore = 0;
	try {
	    stmt = conn.createStatement();
	} catch (Exception e) {
	    WISE_Application.log_error(
		    "WISE - PAGE Store error: Can't get DB statement for user ["
			    + theUser.id + "]: " + e.toString(), null);
	}

	for (int i = 0; i < names.length; i++) {
	    String fieldnm = names[i];
	    String newval = vals[i];
	    if (newval == null || newval.equals(""))
		continue;
	    // convert string (ascii) values for sql storage; may need to
	    // abstract this out if more datatypes
	    if (valTypes[i] == 'a') {
		newval = "'" + fixquotes(newval) + "'";
	    }
	    colNames += "," + fieldnm;
	    values += "," + newval;
	    updateStr += "," + fieldnm + "=VALUES(" + fieldnm + ")";
	    updateTrailStr += ",(" + theUser.id + ",'" + surveyID + "','"
		    + fieldnm + "', " + newval + ")";
	    n_toStore++;
	}
	if (n_toStore > 1) {
	    // chop initial comma
	    updateTrailStr = updateTrailStr.substring(1,
		    updateTrailStr.length());
	    sqlu = "insert into update_trail (invitee, survey, ColumnName, CurrentValue)"
		    + " values " + updateTrailStr;
	    try {
		stmt.execute(sqlu);
	    } catch (Exception e) {
		WISE_Application.log_error("WISE - PAGE Store [" + theUser.id
			+ "] query (" + sqlu + "): " + e.toString(), null);
	    }
	}
	// note proper storage of "status" field relies on User object having
	// advanced page before call;
	String nextPage = "null";
	if (theUser.currentPage != null) // null val means finished
	    nextPage = "'" + theUser.currentPage.id + "'";
	sql = "INSERT into " + mainTable_name + " (invitee, status " + colNames
		+ ") VALUES (" + theUser.id + "," + nextPage + values
		+ ") ON DUPLICATE KEY UPDATE status=VALUES(status) "
		+ updateStr;
	log.info("The data storing sql is " + sql);
	try {
	    stmt.execute(sql);
	} catch (Exception e) {
	    WISE_Application.log_error("WISE - PAGE Store error [" + theUser.id
		    + "] query (" + sql + "): " + e.toString(), null);
	}
	try {
	    stmt.close();
	} catch (Exception e) {
	    WISE_Application.log_error(
		    "WISE - PAGE Store closing error: " + e.toString(), null);
	}

	return 1;
    }

    /** setup user's status entry in survey data table */
    public void begin_survey(String pageID) {
	try {
	    // connect to database
	    Statement stmt = conn.createStatement();
	    // check if the user has already existed in the survey data table
	    String sql = "SELECT status FROM " + mainTable_name
		    + " WHERE invitee = " + theUser.id;
	    stmt.execute(sql);
	    ResultSet rs = stmt.getResultSet();
	    boolean exists = rs.next();
	    // if the user doesn't exist, insert a new user record in to the
	    // data table
	    // and set the status value to be the ID of the 1st survey page -
	    // (starting from the beginning)
	    if (!exists) {
		sql = "INSERT INTO " + mainTable_name
			+ " (invitee, status) VALUES (" + theUser.id + ",'"
			+ pageID + "')";
		stmt.execute(sql);
	    }

	    // update user state to be started (consented)
	    sql = "update survey_user_state set state='started', state_count=1, entry_time=now() where invitee="
		    + theUser.id + " AND survey='" + surveyID + "'";
	    stmt.execute(sql);
	    stmt.close();
	} catch (Exception e) {
	    WISE_Application.log_error("Databank SETUP STATUS:" + e.toString(),
		    null);
	}
    }

    public String get_currentPageName() // returns null if none
    {
	String sql = "SELECT status FROM " + mainTable_name
		+ " WHERE invitee = " + theUser.id;
	// Assumes user/survey has a state
	String status = null;
	try {
	    // connect to database
	    Statement stmt = conn.createStatement();
	    stmt.execute(sql);
	    ResultSet rs = stmt.getResultSet();
	    if (rs.next())
		status = rs.getString(1);
	    rs.close();
	    stmt.close();
	    stmt.close();
	} catch (Exception e) {
	    WISE_Application.log_error(
		    "UDB get_currentPageName:" + e.toString(), e);
	}
	return status;
    }

    // read all previously-submitted main values for the user;
    // return null if not started the survey
    public Hashtable get_main_data() {
	Hashtable h = new Hashtable();
	int i = 0;
	try {
	    Statement stmt = conn.createStatement();
	    // pull all from current survey data table
	    String sql = "SELECT * from " + mainTable_name
		    + " WHERE invitee = " + theUser.id;
	    boolean query_success = stmt.execute(sql);
	    if (!query_success)
		throw new Exception("SQL query failure!!! : " + sql);
	    ResultSet rs = stmt.getResultSet();
	    if (rs.next()) {
		ResultSetMetaData metaData = rs.getMetaData();
		if (metaData == null)
		    throw new Exception("can't get meta data");
		int columns = metaData.getColumnCount();
		String col_name, ans;
		for (i = 1; i <= columns; i++) {
		    col_name = metaData.getColumnName(i);
		    if (col_name == null)
			throw new Exception("can't get column name " + i);
		    ans = rs.getString(col_name);
		    // leave out of the hashtable if null value (hashes can't
		    // hold nulls)
		    if (ans != null)
			h.put(col_name, ans);
		}
	    } else {
		return null;
	    }
	    stmt.close();
	} catch (Exception e) {
	    WISE_Application.log_error("USER_DB SETUP DATA after " + i
		    + " cols read: " + e.toString(), e);
	}
	return h;
    }

    public int insert_update_row_repeating_table(String table_name,
	    String row_id, String row_name,
	    Hashtable<String, String> name_value,
	    Hashtable<String, String> name_type) {

	int insertedKeyValue = -1;

	StringBuffer sql_statement = new StringBuffer("");
	StringBuffer comma_sepd_column_names = new StringBuffer("");
	StringBuffer comma_sepd_column_values = new StringBuffer("");
	StringBuffer comma_sepd_update_string = new StringBuffer("");

	// iterate through hashtable to get column names and types
	Enumeration<String> e_iterator = name_value.keys();
	while (e_iterator.hasMoreElements()) {
	    String column_name = e_iterator.nextElement();
	    comma_sepd_column_names.append(column_name + ",");

	    comma_sepd_update_string.append(column_name + "=VALUES("
		    + column_name + "),");

	    String column_value = name_value.get(column_name);
	    if (name_type.get(column_name).equals("text")
		    || name_type.get(column_name).equals("textarea")) {

		if ("".equals(column_value)) {
		    comma_sepd_column_values.append(" NULL,");
		} else {
		    comma_sepd_column_values.append("'"
			    + fixquotes(column_value) + "'" + ",");
		}

	    } else {
		comma_sepd_column_values.append(column_value + ",");
	    }

	}

	// remove the last commas
	if (comma_sepd_column_names
		.charAt(comma_sepd_column_names.length() - 1) == ',')
	    comma_sepd_column_names.setCharAt(
		    comma_sepd_column_names.length() - 1, ' ');
	if (comma_sepd_column_values
		.charAt(comma_sepd_column_values.length() - 1) == ',')
	    comma_sepd_column_values.setCharAt(
		    comma_sepd_column_values.length() - 1, ' ');
	if (comma_sepd_update_string
		.charAt(comma_sepd_update_string.length() - 1) == ',')
	    comma_sepd_update_string.setCharAt(
		    comma_sepd_update_string.length() - 1, ' ');
	// --end of remove last commas

	sql_statement.append("INSERT INTO ");
	sql_statement.append(table_name);
	if (row_id != null)
	    sql_statement.append(" (instance,invitee,instance_name, ");
	else
	    sql_statement.append("(invitee,instance_name, ");
	sql_statement.append(comma_sepd_column_names.toString() + ") ");
	sql_statement.append("VALUES (");
	if (row_id != null)
	    sql_statement.append(row_id + ",");
	sql_statement.append(theUser.id + ",");
	sql_statement.append("'" + row_name + "',");
	sql_statement.append(comma_sepd_column_values.toString() + ") ");
	sql_statement.append("ON DUPLICATE KEY UPDATE ");
	sql_statement.append(comma_sepd_update_string);
	sql_statement.append("");
	sql_statement.append("");
	sql_statement.append("");
	sql_statement.append("");
	sql_statement.append("");

	log.info(sql_statement.toString());

	Statement statement = null;
	try {
	    statement = conn.createStatement();
	} catch (Exception e) {
	    WISE_Application.log_error(
		    "WISE - Repeat Item Store error: Can't get DB statement for user ["
			    + theUser.id + "]: " + e.toString(), null);
	}

	try {
	    statement.execute(sql_statement.toString(),
		    Statement.RETURN_GENERATED_KEYS);

	    ResultSet generatedKeySet = statement.getGeneratedKeys();


	    if (generatedKeySet.first()) {
		insertedKeyValue = generatedKeySet.getInt(1);
	    }

	} catch (Exception e) {
	    WISE_Application.log_error("WISE - Repeat Item Store error ["
		    + theUser.id + "] query (" + sql_statement.toString()
		    + "): " + e.toString(), null);
	}
	try {
	    statement.close();
	} catch (Exception e) {
	    WISE_Application.log_error(
		    "WISE - Repeat Item Store closing error: " + e.toString(),
		    null);
	}

	return insertedKeyValue;

    }

    public String get_all_data_for_repeating_set(String repeating_set_name) {

	String table_name = "repeat_set_" + repeating_set_name;

	int column_index = 0;

	StringBuffer javascript_array_response = new StringBuffer();

	try {
	    Statement stmt = conn.createStatement();
	    // pull all from current repeating set table
	    String sql = "SELECT * from " + table_name + " WHERE invitee = "
		    + theUser.id;
	    log.info("The sql statement is:" + sql);
	    boolean query_success = stmt.execute(sql);
	    if (!query_success)
		throw new Exception("SQL query failure!!! : " + sql);
	    ResultSet rs = stmt.getResultSet();

	    javascript_array_response.append("{");

	    while (rs.next()) {
		ResultSetMetaData metaData = rs.getMetaData();
		if (metaData == null)
		    throw new Exception("can't get meta data");

		int columns = metaData.getColumnCount();
		String col_name, ans;
		for (column_index = 1; column_index <= columns; column_index++) {

		    col_name = metaData.getColumnName(column_index);
		    if (col_name == null)
			throw new Exception("can't get column name "
				+ column_index);
		    ans = rs.getString(col_name);

		    if (column_index == 1) {
			javascript_array_response.append("\"" + ans + "\""
				+ ":[{");
		    }
		    if (ans != null) {
			javascript_array_response
				.append("\"" + col_name + "\"");
			javascript_array_response.append(":");
			ans = ans.replaceAll("(\r\n|\n\r|\r|\n)", "\\\\n");
			javascript_array_response.append("\"" + ans + "\"");
		    } else {// dont add;
		    }

		    if (!(column_index == columns)) {
			if (ans != null) {
			    javascript_array_response.append(",");
			} else {// dont add;
			}
		    } else {
			// remove the last comma
			if (javascript_array_response
				.charAt(javascript_array_response.length() - 1) == ',') {
			    javascript_array_response
				    .deleteCharAt(javascript_array_response
					    .length() - 1);
			}
			javascript_array_response.append("}],");
		    }
		}

	    }
	    if (javascript_array_response.length() > 2) {
		// remove the last comma
		javascript_array_response
			.deleteCharAt(javascript_array_response.length() - 1);
	    }
	    javascript_array_response.append("}");
	    stmt.close();

	} catch (Exception e) {

	    WISE_Application.log_error("USER_DB REPEATING SET after "
		    + column_index + " cols read: " + e.toString(), e);
	}

	return javascript_array_response.toString();
    }

    /** return all data that the user has stored in the survey data table */
    public Hashtable get_all_data() {
	Hashtable h = new Hashtable();
	try {
	    // connect to the database
	    Statement stmt = conn.createStatement();
	    // get data from database for subject
	    String sql = "select ColumnName, CurrentValue from UPDATE_TRAIL "
		    + "where invitee = " + theUser.id + " AND survey = "
		    + surveyID + " Order by Modified asc";
	    stmt.execute(sql);
	    ResultSet rs = stmt.getResultSet();
	    // ResultSetMetaData metaData = rs.getMetaData();
	    // int columns = metaData.getColumnCount();
	    // the data hash table takes the column name as the key
	    // and the user's anwser as its value
	    while (rs.next()) {
		String col_name, ans;
		col_name = rs.getString(2);
		ans = rs.getString(2);
		// input a string called null if the column value is null
		// to avoid the hash table has the null value
		if (ans == null)
		    ans = "null";
		h.put(col_name, ans); // old, overwritten values will be
		// overwritten here
	    }
	    rs.close();
	    stmt.close();
	} catch (Exception e) {
	    WISE_Application.log_error("USER GET DATA: " + e.toString(), null);
	}
	return h;
    }

    /** update the database to record user's current page */
    public void record_currentPage() {
	String sql = "INSERT INTO " + mainTable_name + " (invitee, status) "
		+ "VALUES (" + theUser.id + ",'" + theUser.currentPage.id
		+ "') " + "on duplicate key update status=values(status)";
	try {
	    Statement stmt = conn.createStatement();
	    stmt.execute(sql);
	    stmt.close();
	} catch (Exception e) {
	    WISE_Application.log_error("Record page STATUS:" + e.toString(),
		    null);
	}
    }

    public void record_pageSubmit() {
	String sql = "INSERT INTO page_submit (invitee, survey, page) "
		+ "VALUES (" + theUser.id + ",'" + surveyID + "', '"
		+ theUser.currentPage.id + "') ";
	try {
	    Statement stmt = conn.createStatement();
	    stmt.execute(sql);
	    stmt.close();
	} catch (Exception e) {
	    WISE_Application.log_error(
		    "Record page submit error:" + e.toString(), null);
	}
    }

    // return the new messageID
    public String record_messageUse(String message_id) {
	String uid = theUser.id;
	String messageId = org.apache.commons.lang3.RandomStringUtils
		.randomAlphanumeric(22);
	String sql = "INSERT INTO survey_message_use(messageId,invitee, survey, message) VALUES ('"
		+ messageId
		+ "',"
		+ uid
		+ ",'"
		+ surveyID
		+ "', '"
		+ message_id + "')";
	String newid = messageId;
	try {
	    // connect to database
	    Statement stmt = conn.createStatement();
	    // check if the user has already existed in the survey data table
	    stmt.execute(sql);

	    stmt.close();
	} catch (Exception e) {
	    WISE_Application.log_error("Error recording new message using "
		    + sql + ": " + e.toString(), null);
	}
	return newid;
    }

    public void set_userState(String newState) {
	String sql = "update survey_user_state set state='" + newState
		+ "', state_count=1 "
		+ // reset to 1 on entering new state
		"where invitee=" + theUser.id + " AND survey='" + surveyID
		+ "'";
	// Assumes user/survey has a state
	try {
	    // connect to database
	    Statement stmt = conn.createStatement();
	    stmt.execute(sql);
	    stmt.close();
	} catch (Exception e) {
	    WISE_Application.log_error("UDB set_userState:" + e.toString(),
		    null);
	}
    }

    public String get_userState() {
	String sql = "SELECT state FROM survey_user_state " + "where invitee="
		+ theUser.id + " AND survey='" + surveyID + "'";
	// Assumes user/survey has a state
	String theState = null;
	try {
	    // connect to database
	    Statement stmt = conn.createStatement();
	    stmt.execute(sql);
	    ResultSet rs = stmt.getResultSet();
	    if (rs.next())
		theState = rs.getString(1);
	    stmt.close();
	} catch (Exception e) {
	    WISE_Application.log_error("UDB get_userState:" + e.toString(),
		    null);
	}
	return theState;
    }

    public String get_current_MessageSequence() {
	String sql = "SELECT message_sequence FROM survey_user_state "
		+ "where invitee=" + theUser.id + " AND survey='" + surveyID
		+ "'";
	// Assumes user/survey has a state
	String theSeq = null;
	try {
	    // connect to database
	    Statement stmt = conn.createStatement();
	    stmt.execute(sql);
	    ResultSet rs = stmt.getResultSet();
	    if (rs.next())
		theSeq = rs.getString(1);
	    stmt.close();
	} catch (Exception e) {
	    WISE_Application.log_error("UDB get_userState:" + e.toString(),
		    null);
	}
	return theSeq;
    }

    /** save the user's consent answer - accept or decline */
    public void set_consent(String answer) {
	try {
	    Statement stmt = conn.createStatement();
	    String sql = "INSERT INTO consent_response (invitee, answer, survey) VALUES ("
		    + theUser.id + ",'" + answer + "', '" + surveyID + "')";
	    stmt.execute(sql);
	    stmt.close();
	} catch (Exception e) {
	    WISE_Application
		    .log_error("USER_DB SET CONSENT:" + e.toString(), e);
	}
    }

    /** check if user has consented - accepted the consent form */
    public boolean check_consent() {
	boolean resultp = false;
	try {
	    Statement stmt = conn.createStatement();
	    // if user accepted the consent form, the record can be found from
	    // the consent_response table
	    resultp = stmt
		    .execute("SELECT * FROM consent_response WHERE invitee = "
			    + theUser.id + " AND survey='" + surveyID
			    + "' AND answer = 'Y'");
	    ResultSet rs = stmt.getResultSet();
	    resultp = rs.next();
	    if (resultp)
		resultp = true;
	    rs.close();
	    stmt.close();
	} catch (Exception e) {
	    WISE_Application.log_error("USER CHECK CONSENT:" + e.toString(), e);
	}
	return resultp;
    }

    /** create user's survey session */
    public String create_survey_session(String browser_useragent,
	    String survey_msgid) {
	String sessionid = "";
	try {
	    // connect to the database
	    Statement statement = conn.createStatement();
	    // add a new session record and save the startime & user's browser
	    // info
	    String sql = "INSERT INTO survey_user_session (from_message, endtime, starttime, browser_info) "
		    + "VALUES ('"
		    + survey_msgid
		    + "', 0, now(), \""
		    + browser_useragent + "\")";
	    statement.execute(sql);
	    // get the new session id
	    sql = "SELECT LAST_INSERT_ID()";
	    statement.execute(sql);
	    ResultSet rs = statement.getResultSet();
	    if (rs.next())
		sessionid = rs.getString(1);
	    statement.close();
	} catch (Exception e) {
	    WISE_Application.log_error(
		    "USER CREATE DB SESSION:" + e.toString(), null);
	}
	return sessionid;
    }

    /** close the user's survey session by setting endtime to now() */
    public void close_survey_session() {
	String sql = "UPDATE survey_user_session SET endtime = now() WHERE id = "
		+ theUser.user_session;
	try {
	    Statement stmt = conn.createStatement();
	    stmt.execute(sql);
	    stmt.close();
	} catch (Exception e) {
	    WISE_Application.log_error(
		    "USER CLOSE SURVEY SESSION :" + e.toString(), null);
	}
    }

    /** update the user's status to be done in the survey data table */
    public void set_done() {
	// set endtime for the current survey session
	String sql = "UPDATE survey_user_session SET endtime = now() WHERE id = "
		+ theUser.user_session;
	// set status = null, which means the user has completed the survey
	String sql2 = "UPDATE " + mainTable_name
		+ " SET status = null WHERE invitee = " + theUser.id;
	try {
	    Statement stmt = conn.createStatement();
	    stmt.execute(sql);
	    stmt.execute(sql2);
	    stmt.close();
	} catch (Exception e) {
	    WISE_Application.log_error("USER SET DONE:" + e.toString(), null);
	}
    }

    /**
     * gets a hashtable of all the page IDs which the user has completed and
     * currently working on
     */
    public Hashtable get_completed_pages() {
	Hashtable pages = new Hashtable();
	try {
	    Statement stmt = conn.createStatement();
	    // get the status' value from the survey data table
	    String sql = "select status from " + mainTable_name
		    + " where invitee =" + theUser.id;
	    stmt.execute(sql);
	    ResultSet rs = stmt.getResultSet();
	    // add it into the hashtable
	    while (rs.next()) {
		pages.put(rs.getString(1), "Current");
	    }
	    // get the submitted page IDs from page submit table
	    sql = "select distinct page from page_submit where invitee="
		    + theUser.id + " and survey='" + surveyID + "'";
	    stmt.execute(sql);
	    rs = stmt.getResultSet();
	    // input them into the hashtable
	    while (rs.next()) {
		pages.put(rs.getString(1), "Completed");
	    }
	    stmt.close();
	} catch (Exception e) {
	    WISE_Application.log_error(
		    "USER DB get_completed_pages:" + e.toString(), e);
	}
	return pages;
    }

    public boolean record_welcome_hit(String inviteeId, String surveyId) {
	Statement stmt = null;
	boolean resultp = false;
	try {
	    // connect to the database
	    stmt = conn.createStatement();
	    // insert a new accessment record
	    String sql = "INSERT INTO welcome_hits (invitee, survey) VALUES ("
		    + inviteeId + ", '" + surveyId + "')";
	    resultp = stmt.execute(sql);
	} catch (Exception e) {
	    WISE_Application.log_error(
		    "USER RECORD WELCOME HIT:" + e.toString(), e);
	} finally {
	    try {
		stmt.close();
	    } catch (SQLException e) {
		log.error(e);
	    }
	}
	return resultp;
    }

    public boolean record_decline_hit(String msgId, String studyId,
	    String inviteeId, String surveryId) {

	boolean resultp = false;
	Statement stmt = null;
	try {
	    // connect to the database
	    stmt = conn.createStatement();
	    // add a new decline hits record
	    String sql = "INSERT INTO decline_hits (msg_id, survey) VALUES ('"
		    + msgId + "', '" + studyId + "')";
	    resultp = stmt.execute(sql);
	    // update the user state
	    sql = "update survey_user_state set state='declined', state_count=1, entry_time=now() where invitee="
		    + inviteeId + " AND survey='" + surveryId + "'";
	    resultp = stmt.execute(sql);
	} catch (Exception e) {
	    WISE_Application.log_error(
		    "USER RECORD DECLINE HIT:" + e.toString(), e);
	} finally {
	    try {
		stmt.close();
	    } catch (SQLException e) {
		log.error(e);
	    }
	}
	return resultp;

    }

    public boolean set_decline_reason(String inviteeId, String reason) {
	Statement stmt = null;
	boolean retVal = false;
	try {
	    // connect to the database
	    stmt = conn.createStatement();
	    // save the user's decline reason
	    String sql = "INSERT INTO decline_reason (invitee, reason) VALUES ("
		    + inviteeId + ", \"" + reason + "\")";
	    retVal = stmt.execute(sql);
	} catch (Exception e) {
	    WISE_Application.log_error(
		    "USER SET DECLINE REASON:" + e.toString(), e);
	} finally {
	    try {
		stmt.close();
	    } catch (SQLException e) {
		log.error(e);
	    }
	}
	return retVal;
    }

    public int check_completion_number(String inviteeId) {
	int num_completers = 0;
	Statement stmt = null;
	try {
	    // connect to the database
	    stmt = conn.createStatement();
	    // count the completers
	    stmt.execute("SELECT count(distinct invitee) FROM " + inviteeId
		    + "_data WHERE status IS NULL");
	    ResultSet rs = stmt.getResultSet();
	    if (rs.next()) {
		num_completers = rs.getInt("count(distinct invitee)");
	    }
	    rs.close();
	} catch (Exception e) {
	    WISE_Application.log_error(
		    "USER CHECK COMPLETION NUMBER:" + e.toString(), e);
	} finally {
	    try {
		stmt.close();
	    } catch (SQLException e) {
		log.error(e);
	    }
	}
	return num_completers;
    }

    /*
     * Pralav: Functions added for deleting repeating item sets
     */

    public boolean deleteRowFromTable(String inviteeId, String tableName,
	    String instanceName) {

	String sqlStatement = "";

	try {
	    sqlStatement += "DELETE FROM " + tableName + " WHERE invitee="
		    + inviteeId + " AND instance_name='" + instanceName + "'";

	    Statement statement = conn.createStatement();

	    statement.executeUpdate(sqlStatement);

	} catch (SQLException e) {
	    log.error("Error for SQL statement: " + sqlStatement, e);
	    return false;
	}

	return true;

    }
    /*
     * TODO: (med) implement subject set storage public int
     * storeSubjectSetData(Hashtable h, String subjSet_name, String pageID) {
     * String sql=""; int storecount=0; try { //connect to the database
     * Connection conn = getDBConnection(); Statement stmt =
     * conn.createStatement();
     * 
     * //then check if a user record exists in table of subject set for (int i =
     * 0; i < stems.length; i++) { sql =
     * "SELECT * from "+page.survey.id+"_"+SubjectSet_name+"_data where "; sql
     * += "invitee = " +theUser.id+" and subject="; sql +=
     * stem_fieldNames[i].substring((stem_fieldNames[i].lastIndexOf("_")+1));
     * dbtype = stmt.execute(sql); rs = stmt.getResultSet(); user_data_exists =
     * rs.next();
     * 
     * Statement stmt2 = conn.createStatement(); //read out the user's new data
     * from the hashtable params String s_new = (String)
     * params.get(stem_fieldNames[i].toUpperCase());
     * 
     * //note that s_new could be null - seperate the null value with the 0
     * value s_new = Study_Util.fixquotes(s_new); if
     * (s_new.equalsIgnoreCase("")) s_new = "NULL";
     * 
     * //if both tables (page_submit & subject set) have the user's data if
     * (user_data_exists) { String s = rs.getString(name); //compare with the
     * new user data, update the subject set data if the old value has been
     * changed if ((s==null && !s_new.equalsIgnoreCase("NULL")) || (s!=null &&
     * !s.equalsIgnoreCase(s_new))) { //create UPDATE statement sql =
     * "update "+page.survey.id+"_"+SubjectSet_name+"_data set "; sql += name +
     * " = " + s_new; sql += " where invitee = "+theUser.id+" and subject="; sql
     * += stem_fieldNames[i].substring((stem_fieldNames[i].lastIndexOf("_")+1));
     * dbtype = stmt2.execute(sql);
     * 
     * String s1; if (s != null) s1 = Study_Util.fixquotes(s); else s1 = "null";
     * //check if the user's record exists in the table of update_trail, update
     * the data there as well sql =
     * "select * from update_trail where invitee="+theUser
     * .id+" and survey='"+page.survey.id; sql +=
     * "' and page='"+page.id+"' and ColumnName='"
     * +stem_fieldNames[i].toUpperCase()+"'"; dbtype = stmt2.execute(sql);
     * ResultSet rs2 = stmt2.getResultSet(); if(rs2.next()) { //update the
     * records in the update trail if(!s1.equalsIgnoreCase(s_new)) { sql =
     * "update update_trail set OldValue='"+s1+"', CurrentValue='"+s_new; sql
     * +="', Modified=now() where invitee="
     * +theUser.id+" and survey='"+page.survey.id; sql
     * +="' and page='"+page.id+"' and ColumnName='"
     * +stem_fieldNames[i].toUpperCase()+"'"; } } //insert new record if it
     * doesn't exist in the table of update_trail else { sql =
     * "insert into update_trail (invitee, survey, page, ColumnName, OldValue, CurrentValue)"
     * ; sql += " values ("+theUser.id+",'"+page.survey.id+"','"+page.id+"','";
     * sql += stem_fieldNames[i].toUpperCase()+"','"+s1+"', '"+s_new+"')"; }
     * dbtype = stmt2.execute(sql); } } //if no user's record exists in both
     * tables else { //create a insert statement to insert this record in the
     * table of subject set sql =
     * "insert into "+page.survey.id+"_"+SubjectSet_name+"_data "; sql +=
     * "(invitee, subject, "+name+") "; sql += "values ("+theUser.id+",'"; sql
     * += Study_Util.fixquotes(stem_fieldNames[i].substring((stem_fieldNames[i].
     * lastIndexOf("_")+1))); sql += "', "+s_new+")"; dbtype =
     * stmt2.execute(sql); //and insert record into the table of update_trail as
     * well sql =
     * "insert into update_trail (invitee, survey, page, ColumnName, OldValue, CurrentValue)"
     * ; sql += " values ("+theUser.id+",'"+page.survey.id+"','"+page.id+"','";
     * sql += stem_fieldNames[i].toUpperCase()+"','null', '"+s_new+"')"; dbtype
     * = stmt2.execute(sql); } stmt2.close(); } //end of for loop stmt.close();
     * conn.close(); } //end of try catch (Exception e) {
     * Study_Util.email_alert(
     * "WISE - QUESTION BLOCK ["+page.id+"] READ FORM ("+sql
     * +"): "+e.toString()); } } //end of else return index_len;
     */
}
