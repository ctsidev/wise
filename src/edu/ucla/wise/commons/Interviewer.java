package edu.ucla.wise.commons;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/** This class represents an interviewer object */

public class Interviewer {
    /** Instance Variables */
    public Study_Space study_space;

    public String id;

    public String user_name;
    public String email;
    public String first_name;
    public String last_name;
    public String salutation;
    public String login_time;

    public String interview_session_id;
    public String interview_assign_id;

    public Study_Space getStudy_space() {
	return study_space;
    }

    public void setStudy_space(Study_Space study_space) {
	this.study_space = study_space;
    }

    public String getId() {
	return id;
    }

    public void setId(String id) {
	this.id = id;
    }

    public String getUser_name() {
	return user_name;
    }

    public void setUser_name(String user_name) {
	this.user_name = user_name;
    }

    public String getEmail() {
	return email;
    }

    public void setEmail(String email) {
	this.email = email;
    }

    public String getFirst_name() {
	return first_name;
    }

    public void setFirst_name(String first_name) {
	this.first_name = first_name;
    }

    public String getLast_name() {
	return last_name;
    }

    public void setLast_name(String last_name) {
	this.last_name = last_name;
    }

    public String getSalutation() {
	return salutation;
    }

    public void setSalutation(String salutation) {
	this.salutation = salutation;
    }

    public String getLogin_time() {
	return login_time;
    }

    public void setLogin_time(String login_time) {
	this.login_time = login_time;
    }

    /** constructor: create an interviewer object */
    public Interviewer(Study_Space study_space) {
	this.study_space = study_space;
    }

    /**
     * check the interviewer's verification when logging in and assign the
     * attributes
     */
    public boolean verify_interviewer(String interview_id,
	    String interview_username) {
	boolean get_result = false;
	id = interview_id;
	user_name = interview_username;

	try {
	    // connect to the database
	    Connection conn = study_space.getDBConnection();
	    Statement statement = conn.createStatement();
	    Statement statement_1 = conn.createStatement();
	    // check if the record exists in the table of interviewer
	    String sql = "select firstname, lastname, salutation, email, submittime from interviewer where id='"
		    + id + "' and username='" + user_name + "'";
	    boolean results = statement.execute(sql);
	    ResultSet rs = statement.getResultSet();

	    login_time = null;
	    // if the interviewer exists in the current database
	    if (rs.next()) {
		// update the login time
		String sql_1 = "update interviewer set submittime=now() where id='"
			+ id + "'";
		boolean result_1 = statement_1.execute(sql_1);
		// assign the attributes
		sql_1 = "select firstname, lastname, salutation, email, submittime from interviewer where id='"
			+ id + "'";
		result_1 = statement_1.execute(sql_1);
		ResultSet rs_1 = statement_1.getResultSet();
		login_time = null;
		if (rs_1.next()) {
		    first_name = rs.getString("firstname");
		    last_name = rs.getString("lastname");
		    salutation = rs.getString("salutation");
		    email = rs.getString("email");
		    login_time = rs.getString("submittime");
		    get_result = true;
		}
		rs_1.close();
		statement_1.close();
	    }
	    rs.close();
	    statement.close();
	    conn.close();
	} catch (Exception e) {
	    WISE_Application.log_error(
		    "INTERVIEWER - VERIFY INTERVIEWER:" + e.toString(), null);
	    get_result = false;
	}
	return get_result;
    }

    /**
     * create an interview survey message in the table of survey_message_use
     * before starting the interview
     */
    public String create_survey_message(String invitee_id, String survey_id) {
	String survey_msg_id = null;
	try {
	    // connect to the database
	    Connection conn = study_space.getDBConnection();
	    Statement statement = conn.createStatement();
		String messageId = org.apache.commons.lang3.RandomStringUtils
			.randomAlphanumeric(22);
	    // insert an interview record
	    String sql = "INSERT INTO survey_message_use (invitee, survey, message, sent_date) "
		    + " values ('"
			+ messageId
		    + "','"
		    + invitee_id
		    + "','"
		    + survey_id
		    + "','interview', now())";
	    statement.execute(sql);
	    survey_msg_id = messageId;

	    statement.close();
	    conn.close();
	} catch (Exception e) {
	    WISE_Application.log_error(
		    "INTERVIEW - CREATE SURVEY MESSAGE:" + e.toString(), null);
	}
	return survey_msg_id;
    }

    /**
     * create an interview session in the table of interview_session when
     * starting the interview
     */
    public void begin_session(String user_session) {
	// the interview_session_id is a foreign key reference to the user's
	// survey session id
	interview_session_id = user_session;
	// the interview_assign_id is a foreign key reference to the interviewer
	// assignment id
	// which value has been assigned in the Begin_Interview.jsp

	try {
	    // connect to the database
	    Connection conn = study_space.getDBConnection();
	    Statement statement = conn.createStatement();
	    // insert a session record
	    String sql = "INSERT INTO interview_session (session_id, assign_id) VALUES ('"
		    + user_session + "','" + interview_assign_id + "')";
	    boolean results = statement.execute(sql);
	    statement.close();
	    conn.close();
	} catch (Exception e) {
	    WISE_Application.log_error(
		    "INTERVIEW - BEGIN SESSION:" + e.toString(), null);
	}
    }

    /**
     * save the interview session info in the table of interview_assignment
     * before ending the session
     */
    public void set_done() {
	try {
	    // connect to the database
	    Connection conn = study_space.getDBConnection();
	    Statement statement = conn.createStatement();
	    String sql = "UPDATE interview_assignment SET close_date = now(), pending=0 WHERE id = "
		    + interview_assign_id;
	    boolean results = statement.execute(sql);
	    statement.close();
	    conn.close();
	} catch (Exception e) {
	    WISE_Application.log_error("INTERVIEW - SET DONE:" + e.toString(),
		    null);
	}
    }

}
