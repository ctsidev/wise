package edu.ucla.wise.commons;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;

import edu.ucla.wise.initializer.StudySpaceParametersProvider;
import edu.ucla.wise.studyspace.parameters.StudySpaceParameters;

/*
 Admin information set -- 
 The class represents that Admin application
 Instances represent administrator user sessions
 TODO (med): untangle Frank's survey uploading spaghetti in load_data.jsp
 */
/**
 * This class represents the Admin information when running the Admin
 * application.
 * 
 * @author mrao
 * @author dbell
 * @author ssakdeo
 */
public class AdminInfo extends WISE_Application {

    private final Logger log = Logger.getLogger(AdminInfo.class);

    public static String db_backup_path, style_root_path, image_root_path;
    private static Hashtable<String, String> loggedIn = new Hashtable<String, String>();

    /* instance variables -- represent an individual administrator session */
    public Study_Space myStudySpace;
    // public String db_user; now only in databank
    public String db_pwd;
    public String study_id, study_name, study_title;
    public String study_xml_path, study_css_path, study_image_path;
    public String email_format, coda, html_signature;
    public boolean pw_valid;

    // 08-Nov-2009
    // Pushed down into the children classes like Surveyor_Application
    // and AdminInfo as static in the children
    // Make Surveyor_Application a singleton class per JBOSS
    public static String ApplicationName = null;

    public static String shared_file_url;
    public static String shared_image_url;
    public static String servlet_url;

    private static final ConcurrentHashMap<String, Integer> loginAttemptNumbers = new ConcurrentHashMap<String, Integer>();
    private static final ConcurrentHashMap<String, Long> lastLoginTime = new ConcurrentHashMap<String, Long>();

    public static void initStaticFields(String appContext) {
	if (ApplicationName == null) {
	    ApplicationName = appContext;
	}
	shared_file_url = WISE_Application.rootURL + "/" + ApplicationName
		+ "/" + WISE_Application.shared_files_link + "/";
	shared_image_url = shared_file_url + "images/";
	servlet_url = WISE_Application.rootURL + ApplicationName + "/";
    }

    // can't abstract this to WISE_Application because parent's static method
    // doesn't call sub's initialize()
    // NB: there may be a better way to handle this in Java but I can't find it
    // at the moment
    public static String check_init(String appContext) throws IOException {
	String initErr = null;
	if (ApplicationName == null)
	    initErr = initialize(appContext);
	if (ApplicationName == null) // *still* null means uninitialized
	    initErr = "Wise Admin Application -- uncaught initialization error";
	return initErr;
    }

    public static String force_init(String appContext) throws IOException {
	String initErr = null;
	initialize(appContext);
	if (ApplicationName == null) // *still* null means uninitialized
	    initErr = "Wise Admin Application -- uncaught initialization error";
	return initErr;
    }

    public static String initialize(String appContext) throws IOException {
	AdminInfo.initStaticFields(appContext);
	String initErr = WISE_Application.initialize();
	image_root_path = sharedProps.getString("shared_image.path");
	style_root_path = sharedProps.getString("shared_style.path");
	db_backup_path = sharedProps.getString("db_backup.path")
		+ System.getProperty("file.separator");
	// don't need further error checking; prob ok if these are also null
	return initErr;
    }

    /**
     * Admin Info functions Note: need to improve encapsulation; store Admin
     * Info
     * */

    /** constructor to create an Admin user session */
    public AdminInfo(String username, String password_given)
	    throws IllegalArgumentException {


	StudySpaceParameters params = StudySpaceParametersProvider
		.getInstance().getStudySpaceParameters(username);

	if (params == null) {
	    log_info("params object is null");
	    throw new IllegalArgumentException();
	}

	db_pwd = params.getDatabasePassword();
	// 20dec db_pwd = sharedProps.getString(username + ".dbpass");

	log_info("Given password is " + password_given
		+ " and actual password is " + db_pwd);

	pw_valid = password_given.equalsIgnoreCase(db_pwd);
	if (pw_valid)
	    // get other properties
		study_name = username;
		study_id = params.getId();
		// 20dec study_id = sharedProps.getString(username +
		// ".studyid");
		// get or instantiate the Study_Space, which contains the
		// Data_Bank for db access
		myStudySpace = Study_Space.get_Space(study_id);
		study_title = myStudySpace.title;

		// assign other attributes
		study_xml_path = xml_loc + System.getProperty("file.separator")
			+ study_name + System.getProperty("file.separator");
		study_css_path = style_root_path
			+ System.getProperty("file.separator") + study_name
			+ System.getProperty("file.separator");
		study_image_path = image_root_path
			+ System.getProperty("file.separator") + study_name
			+ System.getProperty("file.separator");

		// record Admin user login
		loggedIn.put(study_name, study_id);
		log_info("Study name and study id inserted in loggedIn");
    }

    // finalize() called by garbage collector to clean up all objects
    @Override
    protected void finalize() throws Throwable {
	try {
	    loggedIn.remove(study_name);
	} catch (Exception e) {
	    WISE_Application.log_error("Exception deleting Admin user "
		    + study_name + ": " + e.toString(), e);
	} finally {
	    super.finalize();
	}
    }

    public static String listAdminsOnNow() {
	String adminlist = "";
	Enumeration en = loggedIn.keys();
	while (en.hasMoreElements())
	    adminlist += "<P>" + en.nextElement() + "</P>";
	return adminlist;
    }

    /** returns a database Connection object */
    // TODO: move all database work into Data_Bank class
    public Connection getDBConnection() throws SQLException {
	// Regist the mysql jdbc driver
	// DriverManager.registerDriver(new com.mysql.jdbc.Driver());
	// return
	// DriverManager.getConnection(mysql_url+db_name+"?user="+db_user+"&password="+db_pwd);
	return myStudySpace.getDBConnection();
    }

    /** send an email alert */
    public static void log_error(String body, Exception e) {
	WISE_Application.log_error(body, e);
    }

    /** Ask local copy of the Study_Space to parse out the preface file */
    public boolean parse_message_file() {
	return myStudySpace.load_preface();
    }

    // print the message body
    // retrieve using sequence, message type -- guaranteed
    public String render_message_body(String seq_id, String msg_type) {
	String outputString = "";
	outputString += "<table width=510 class=tth border=1 cellpadding=2 cellspacing=0 bgcolor=#FFFFF5>";
	outputString += "<tr><td width=50 class=sfon>From: </td>";

	try {
	    // get the message sequence from the hash
	    Message_Sequence msg_seq = myStudySpace.preface
		    .get_message_sequence(seq_id);
	    if (msg_seq == null) {
		log_info("ADMIN INFO - PRINT MESSAGE BODY: Can't get the message sequence for requested Sequence, Message Type");
		return null;
	    }

	    String email_from = msg_seq.from_string;
	    email_from = email_from.replaceAll("<", "&lt;");
	    email_from = email_from.replaceAll(">", "&gt;");
	    outputString += "<td>\"" + email_from + "\"";
	    outputString += "</td></tr>";

	    // get the message from the message sequence hash
	    Message m = msg_seq.get_type_message(msg_type);
	    if (m == null) {
		log_info("ADMIN INFO - PRINT MESSAGE BODY: Can't get the message from sequence hash");
		return null;
	    }

	    outputString += m.renderSample_asHtmlRows() + "</table>";
	} catch (Exception e) {
	    log_error("ADMIN INFO - PRINT MESSAGE BODY: " + e.toString(), e);
	}
	return outputString;

    }

    // print the message body
    // retrieve using survey, irb (not guaranteed)
    // I believe this is deprecated --DB
    // public String print_message_body(String survey_id, String irb_id, String
    // msg_type)
    // {
    // String outputString="";
    // outputString +=
    // "<table width=510 class=tth border=1 cellpadding=2 cellspacing=0 bgcolor=#FFFFF5>";
    //
    // try
    // {
    // //get the message sequence from the hash
    // Message_Sequence msg_seq = (Message_Sequence)
    // myStudySpace.preface.get_message_sequence(survey_id, irb_id);
    // if(msg_seq == null)
    // {
    // log_error("ADMIN INFO - PRINT MESSAGE BODY: Can't get the message sequence for requested Survey, IRB");
    // return null;
    // }
    //
    // outputString += "<tr><td width=50 class=sfon>From: </td>";
    // String email_from = msg_seq.from_string;
    // email_from = email_from.replaceAll("<", "&lt;");
    // email_from = email_from.replaceAll(">", "&gt;");
    // outputString +="<td>\""+ email_from +"\"";
    // outputString += "</td></tr>";
    //
    // //get the message from the message sequence hash
    // Message m = (Message) msg_seq.get_type_message(msg_type);
    // if(m == null)
    // {
    // log_error("ADMIN INFO - PRINT MESSAGE BODY: Can't get the requested message "
    // + msg_type +
    // " from Message sequence " + msg_seq.id);
    // return null;
    // }
    // outputString += m.renderSample_asHtmlRows() + "</table>";
    // }
    // catch (Exception e)
    // {
    // log_error("ADMIN INFO - PRINT MESSAGE BODY: "+e.toString());
    // }
    // return outputString;
    //
    // }

    /** print invitees with state - excluding the initial invitees */
    public String print_invitee_with_state(String survey_id) {
	String outputString = "";
	try {
	    Connection conn = this.getDBConnection();
	    Statement stmt = conn.createStatement();
	    Statement stmtm = conn.createStatement();
	    String sql = "SELECT i.id, firstname, lastname, salutation, irb_id, state, email FROM invitee as i, survey_user_state as s where i.id=s.invitee and survey='"
		    + survey_id + "' ORDER BY i.id";
	    stmt.execute(sql);
	    ResultSet rs = stmt.getResultSet();
	    outputString += "<table class=tth border=1 cellpadding=2 cellspacing=0 bgcolor=#FFFFF5>";
	    outputString += "<tr>";
	    outputString += "<th class=sfon></th>";
	    outputString += "<th class=sfon>User ID</th>";
	    outputString += "<th class=sfon>User Name</th>";
	    outputString += "<th class=sfon>IRB ID</th>";
	    outputString += "<th class=sfon>User state</th>";
	    outputString += "<th class=sfon>User's Email Address</th></tr>";

	    while (rs.next()) {
		String sqlm = "select invitee from survey_user_state where state='declined' and invitee="
			+ rs.getString(1);
		stmtm.execute(sqlm);
		ResultSet rsm = stmtm.getResultSet();
		if (rsm.next())
		    outputString += "<tr bgcolor='#E4E4E4'>";
		else
		    outputString += "<tr>";
		outputString += "<td><input type='checkbox' name='user' value='"
			+ rs.getString(1) + "'></td>";
		outputString += "<td>" + rs.getString(1) + "</td>";
		outputString += "<td>" + rs.getString(4) + " "
			+ rs.getString(2) + " " + rs.getString(3) + "</td>";
		outputString += "<td>" + rs.getString(5) + "</td>";
		outputString += "<td>" + rs.getString(6) + "</td>";
		outputString += "<td>" + rs.getString(7) + "</td>";
		outputString += "</tr>";
	    }
	    rs.close();
	    outputString += "</table>";
	    conn.close();
	} catch (Exception e) {
	    log_error("ADMIN INFO - PRINT INVITEE WITH STATE: " + e.toString(),
		    e);
	}
	return outputString;
    }

    /** get the irb groups */
    public Hashtable get_irb_groups() {
	Hashtable irb_groups = new Hashtable();
	try {
	    Connection conn = this.getDBConnection();
	    Statement statement = conn.createStatement();
	    Statement stmt = conn.createStatement();
	    // select the invitees without any states
	    String sqle = "select distinct(irb_id) from invitee order by irb_id";
	    boolean resulte = statement.execute(sqle);
	    ResultSet rse = statement.getResultSet();
	    while (rse.next()) {
		String irb_id = rse.getString("irb_id");
		if (irb_id == null)
		    irb_id = "IS NULL";
		else
		    irb_id = "='" + irb_id + "'";
		String sql = "select id from invitee where irb_id " + irb_id;
		boolean dbtype = stmt.execute(sql);
		ResultSet rs = stmt.getResultSet();
		String irb_groups_id = " ";
		while (rs.next()) {
		    irb_groups_id += rs.getString(1) + " ";
		}
		irb_groups.put(irb_id, irb_groups_id);
	    }
	} catch (Exception e) {
	    log_error("ADMIN INFO - GET IRB GROUPS: " + e.toString(), e);
	}
	return irb_groups;
    }

    /**
     * print table of initial invites, eligible invitees for a survey, by
     * message sequence (& therefore irb ID)
     */
    public String render_initial_invite_table(String survey_id,
	    boolean isReminder) {

	String outputString = "";
	Message_Sequence[] msg_seqs = myStudySpace.preface
		.get_message_sequences(survey_id);
	if (msg_seqs.length == 0)
	    return "No message sequences found in Preface file for selected Survey.";
	try {
	    Connection conn = this.getDBConnection();
	    Statement stmt = conn.createStatement();
	    for (int i = 0; i < msg_seqs.length; i++) {
		Message_Sequence msg_seq = msg_seqs[i];
		String irb_name = msg_seq.irb_id;
		if (CommonUtils.isEmpty(irb_name))
		    irb_name = "= ''";
		else
		    irb_name = "= '" + irb_name + "'";
		Message msg = msg_seq.invite_msg;
		outputString += "<form name=form1 method=post action='initial_invite_send.jsp'>\n"
			+ "<input type='hidden' name='seq' value='"
			+ msg_seq.id
			+ "'>\n"
			+ "<input type='hidden' name='reminder' value='"
			+ String.valueOf(isReminder)
			+ "'>\n"
			+ "<input type='hidden' name='svy' value='"
			+ survey_id
			+ "'>\n"
			+ "Start Message Sequence <B>"
			+ msg_seq.id
			+ "</b> (designated for IRB "
			+ irb_name
			+ ")<BR>\n"
			+ "...using Initial Message: "
			+ "<a href='print_msg_body.jsp?seqID="
			+ msg_seq.id
			+ "&msgID=invite' target='_blank'>"
			+ msg.subject
			+ "</a><br>\n"
			+ "<p align=center><input type='image' alt='Click to send email. This button is equivalent to the one at bottom.' "
			+ "src='admin_images/send.gif'></p>"
			+ "<table class=tth border=1 cellpadding=2 cellspacing=0 bgcolor=#FFFFF5>";
		try {
		    // select the invitees without any states
		    String sql = buildInitialInviteQuery(survey_id, msg_seq.id,
			    irb_name, isReminder);
		    stmt.execute(sql);
		    ResultSet rs = stmt.getResultSet();
		    outputString += "<tr>";
		    outputString += "<th class=sfon></th>";
		    outputString += "<th class=sfon>User ID</th>";
		    outputString += "<th class=sfon>User Name</th>";
		    outputString += "<th class=sfon>IRB</th>";
		    outputString += "<th class=sfon>User's Email Address</th></tr>";

		    while (rs.next()) {
			outputString += "<tr>";
			outputString += "<td><input type='checkbox' name='user' value='"
				+ rs.getString(1) + "'></td>";
			outputString += "<td>" + rs.getString(1) + "</td>";
			outputString += "<td>" + rs.getString(4) + " "
				+ rs.getString(2) + " " + rs.getString(3)
				+ "</td>";
			outputString += "<td>" + rs.getString(5) + "</td>";
			outputString += "<td>" + rs.getString(6) + "</td>";
			outputString += "</tr>";
		    }
		    rs.close();
		} catch (Exception e) {
		    log_error(
			    "ADMIN INFO error - render_initial_invite_table: "
				    + e.toString(), e);
		}
		outputString += "</table><p align='center'>"
			+
			// TODO: resolve file path references between admin and
			// survey applications
			"<input type='image' alt='Click to send email. This button is the same as one above.' src='admin_images/send.gif'>"
			+ "</p></form>";
	    } // for
	    conn.close();
	} catch (Exception e) {
	    log_error(
		    "ADMIN INFO DB connection error - render_initial_invite_table: "
			    + e.toString(), e);
	}
	return outputString;
    }

    private String buildInitialInviteQuery(String survey_id, String msg_seq,
	    String irb_name, boolean isReminder) {
	StringBuffer strBuff = new StringBuffer();
	if (isReminder) {
	    strBuff.append("SELECT I.id, I.firstname, I.lastname, I.salutation, I.irb_id, AES_DECRYPT(I.email,'"
		    + this.myStudySpace.db.email_encryption_key
		    + "') FROM invitee as I, survey_user_state as S WHERE I.irb_id "
		    + irb_name
		    + " AND I.id not in (select invitee from survey_user_state where survey='"
		    + survey_id
		    + "' AND state like 'completed') AND I.id=S.invitee AND S.message_sequence='"
		    + msg_seq + "' ORDER BY id");
	} else {
	    strBuff.append("SELECT id, firstname, lastname, salutation, irb_id, AES_DECRYPT(email,'"
		    + this.myStudySpace.db.email_encryption_key
		    + "') FROM invitee WHERE irb_id "
		    + irb_name
		    + " AND id not in (select invitee from survey_user_state where survey='"
		    + survey_id + "')" + "ORDER BY id");
	}
	return strBuff.toString();
    }

    /**
     * print table of all sendable invites, all invitees, by message sequence (&
     * therefore irb ID)
     */
    public String render_invite_table(String survey_id) {
	String outputString = "";
	Message_Sequence[] msg_seqs = myStudySpace.preface
		.get_message_sequences(survey_id);
	if (msg_seqs.length == 0)
	    return "No message sequences found in Preface file for selected Survey.";
	try {
	    Connection conn = this.getDBConnection();
	    Statement stmt = conn.createStatement();
	    for (int i = 0; i < msg_seqs.length; i++) {
		Message_Sequence msg_seq = msg_seqs[i];
		String irb_name = msg_seq.irb_id;
		if (irb_name.equalsIgnoreCase(""))
		    irb_name = "= ''";
		else
		    irb_name = "= '" + irb_name + "'";
		Message msg = msg_seq.invite_msg;
		outputString += "<form name=form1 method=post action='invite_send.jsp'>\n"
			+ "<input type='hidden' name='seq' value='"
			+ msg_seq.id
			+ "'>\n"
			+ // repeat form so we can use same hidden field names
			  // on each
			"<input type='hidden' name='svy' value='"
			+ survey_id
			+ "'>\n"
			+ "Using Message Sequence <B>"
			+ msg_seq.id
			+ "</b> (designated for IRB "
			+ irb_name
			+ ")<BR>\n"
			+ "...SEND Message: <BR>"
			+ "<input type='radio' name='message' value='invite'>\n"
			+ "<a href='print_msg_body.jsp?seqID="
			+ msg_seq.id
			+ "&msgID=invite' target='_blank'>"
			+ msg.subject
			+ "</a><br>\n";
		for (int j = 0; j < msg_seq.total_other_messages(); j++) {
		    msg = msg_seq.get_type_message("" + j);
		    outputString += "<input type='radio' name='message' value='"
			    + j
			    + "'>\n"
			    + "<a href='print_msg_body.jsp?seqID="
			    + msg_seq.id
			    + "&msgID="
			    + j
			    + "' target='_blank'>"
			    + msg.subject + "</a><br>\n";
		}
		outputString += "<p align=center><input type='image' alt='Click to send email. This button is equivalent to the one at bottom.' "
			+ "src='admin_images/send.gif'></p>"
			+ "<table class=tth border=1 cellpadding=2 cellspacing=0 bgcolor=#FFFFF5>";
		try {
		    // select the invitees without any states
		    String sql = "SELECT id, firstname, lastname, salutation, irb_id, AES_DECRYPT(email, '"
			    + this.myStudySpace.db.email_encryption_key
			    + "') FROM invitee WHERE irb_id "
			    + irb_name
			    + " ORDER BY id";
		    stmt.execute(sql);
		    ResultSet rs = stmt.getResultSet();
		    outputString += "<tr>";
		    outputString += "<th class=sfon></th>";
		    outputString += "<th class=sfon>User ID</th>";
		    outputString += "<th class=sfon>User Name</th>";
		    outputString += "<th class=sfon>IRB</th>";
		    outputString += "<th class=sfon>User's Email Address</th></tr>";

		    while (rs.next()) {
			outputString += "<tr>";
			outputString += "<td><input type='checkbox' name='user' value='"
				+ rs.getString(1) + "'></td>";
			outputString += "<td>" + rs.getString(1) + "</td>";
			outputString += "<td>" + rs.getString(4) + " "
				+ rs.getString(2) + " " + rs.getString(3)
				+ "</td>";
			outputString += "<td>" + rs.getString(5) + "</td>";
			outputString += "<td>" + rs.getString(6) + "</td>";
			outputString += "</tr>";
		    }
		    rs.close();
		} catch (Exception e) {
		    log_error(
			    "ADMIN INFO error - render_initial_invite_table: "
				    + e.toString(), e);
		}
		outputString += "</table><p align='center'>"
			+
			// TODO: resolve file path references between admin and
			// survey applications
			"<input type='image' alt='Click to send email. This button is the same as one above.' src='admin_images/send.gif'>"
			+ "</p></form>";
	    } // for
	    conn.close();
	} catch (Exception e) {
	    log_error(
		    "ADMIN INFO DB connection error - render_initial_invite_table: "
			    + e.toString(), e);
	}
	return outputString;
    }

    /**
     * print initial inviteee in a table format for editing -- called by
     * load_invitee.jsp
     */
    public String print_initial_invitee_editable(String survey_id) {
	String outputString = "";
	try {
	    Connection conn = this.getDBConnection();
	    Statement stmt = conn.createStatement();
	    // select the invitees without any states
	    String sql = "SELECT id, firstname, lastname, salutation, irb_id, AES_DECRYPT(email, '"
		    + this.myStudySpace.db.email_encryption_key
		    + "') FROM invitee WHERE id not in (select invitee from survey_user_state where survey='"
		    + survey_id + "') ORDER BY id";
	    stmt.execute(sql);
	    ResultSet rs = stmt.getResultSet();
	    outputString += "<table class=tth border=1 cellpadding=2 cellspacing=0 bgcolor=#FFFFF5>";
	    outputString += "<tr>";
	    // outputString += "<th class=sfon></th>";
	    outputString += "<th class=sfon>User ID</th>";
	    outputString += "<th class=sfon>First name</th>";
	    outputString += "<th class=sfon>Last name</th>";
	    outputString += "<th class=sfon>IRB</th>";
	    outputString += "<th class=sfon>User's Email Address</th>";
	    outputString += "<th class=sfon>Action</th></tr>";

	    while (rs.next()) {
		outputString += "<tr>";
		// outputString +=
		// "<td><input type='checkbox' name='user' value='"+rs.getString(1)+"'></td>";
		outputString += "<td>" + rs.getString(1) + "</td>";
		outputString += "<td><input type='text' name='fname"
			+ rs.getString(1) + "' value='" + rs.getString(2)
			+ "'/></td>";
		outputString += "<td><input type='text' name='lname"
			+ rs.getString(1) + "' value='" + rs.getString(3)
			+ "'/></td>";
		outputString += "<td><input type='text' name='irb"
			+ rs.getString(1) + "' value='" + rs.getString(5)
			+ "'/></td>";
		outputString += "<td><input type='text' name='email"
			+ rs.getString(1) + "' value='" + rs.getString(6)
			+ "'/></td>";
		outputString += "<td><a href='javascript:update_inv("
			+ rs.getString(1) + ");'> Update </a><br>"
			+ "<a href='javascript:delete_inv(" + rs.getString(1)
			+ ");'> Delete </a>" + "</td>";
		outputString += "</tr>";
	    }
	    rs.close();
	    outputString += "</table>";
	    conn.close();
	} catch (Exception e) {
	    log_error(
		    "ADMIN INFO - PRINT INITIAL INVITEE EDITABLE: "
			    + e.toString(), e);
	}
	return outputString;
    }

    public void update_invitees(HttpServletRequest request) {

	String delFlag = request.getParameter("delflag");
	String updateID = request.getParameter("changeID");
	PreparedStatement stmt = null;
	Connection conn = null;

	try {
	    conn = this.getDBConnection();
	    if (delFlag != null && delFlag.equals("true") && updateID != null) {
		stmt = conn.prepareStatement("delete from invitee where id = "
			+ updateID);
	    } else if (updateID != null) {
		stmt = conn
			.prepareStatement("update invitee set firstname=?, lastname=?, irb_id=?, email="
				+ "AES_ENCRYPT(?,'"
				+ this.myStudySpace.db.email_encryption_key
				+ "')" + " where id=?");
		String irbid = request.getParameter("irb" + updateID);
		if (irbid.equals("") || irbid.equalsIgnoreCase("null"))
		    irbid = null;
		stmt.setString(1, request.getParameter("fname" + updateID));
		stmt.setString(2, request.getParameter("lname" + updateID));
		stmt.setString(3, irbid);
		stmt.setString(4, request.getParameter("email" + updateID));
		stmt.setString(5, updateID);
	    }
	    if (stmt != null)
		stmt.execute();
	} catch (SQLException e) {
	    log.error("Deleting/Updating the invitee failed.", e);
	    log_error("ADMIN INFO - UPDATE INVITEE: " + e.toString(), e);
	} finally {
	    if (stmt != null) {
		try {
		    stmt.close();
		} catch (SQLException e) {
		    // That's Okie!
		    log.error("check why prepared statement creation failed", e);
		}
	    }
	    if (conn != null) {
		try {
		    conn.close();
		} catch (SQLException e) {
		    // That's Okie!
		    log.error("check why prepared statement creation failed", e);
		}
	    }
	}
    }

    /** print interviewer list for a study_space */
    public String print_interviewer() {
	String outputString = "";
	try {
	    Connection conn = this.getDBConnection();
	    Statement stmt = conn.createStatement();
	    String sql = "SELECT id, firstname, lastname, salutation, email FROM interviewer ORDER BY id";
	    boolean dbtype = stmt.execute(sql);
	    ResultSet rs = stmt.getResultSet();
	    outputString += "<table class=tth border=1 cellpadding=2 cellspacing=0 bgcolor=#FFFFF5>";
	    outputString += "<tr>";
	    outputString += "<th class=sfon></th>";
	    outputString += "<th class=sfon>Interviewer ID</th>";
	    outputString += "<th class=sfon>Interviewer Name</th>";
	    outputString += "<th class=sfon>Interviewer's Email Address</th>";
	    outputString += "<th class=sfon>Go to WATI</th></tr>";

	    while (rs.next()) {
		outputString += "<tr>";
		outputString += "<td><input type='radio' name='interviewer' value='"
			+ rs.getString(1) + "'></td>";
		outputString += "<td align=center>" + rs.getString(1) + "</td>";
		outputString += "<td align=center>" + rs.getString(4) + " "
			+ rs.getString(2) + " " + rs.getString(3) + "</td>";
		outputString += "<td>" + rs.getString(5) + "</td>";
		outputString += "<td align=center><a href='goto_wati.jsp?interview_id="
			+ rs.getString(1)
			+ "'><img src='admin_images/go_view.gif' border=0></a></td>";
		outputString += "</tr>";
	    }
	    rs.close();
	    outputString += "</table>";
	    conn.close();
	} catch (Exception e) {
	    AdminInfo.log_error(
		    "ADMIN INFO - PRINT INTERVIEWER LIST:" + e.toString(), e);
	}
	return outputString;
    }

    public String send_messages(String msg_type, String message_seq_id,
	    String survey_id, String whereStr, boolean isReminder) {
	return myStudySpace.sendInviteReturnDisplayMessage(msg_type,
		message_seq_id, survey_id, whereStr, isReminder);
    }

    /** get non-responders and incompleters id */
    public void get_nonresponders_incompleters(String[] sp_id, String s_id) {

	try {
	    String nonresponder_id = " ";
	    String incompleter_id = " ";
	    // connect to the database
	    Connection conn = getDBConnection();
	    Statement stmt = conn.createStatement();
	    // get the non-responders user ID list
	    String sql = "select distinct(s.invitee) from survey_message_use as s, invitee as i where s.survey='"
		    + s_id
		    + "' and s.invitee=i.id "
		    + "and s.invitee not in (select invitee from consent_response where answer='N') "
		    + "and not exists (select u.invitee from "
		    + s_id
		    + "_data as u where u.invitee=s.invitee) "
		    + "group by s.invitee order by s.invitee";

	    boolean dbtype = stmt.execute(sql);
	    ResultSet rs = stmt.getResultSet();
	    while (rs.next()) {
		nonresponder_id += rs.getString(1) + " ";
	    }

	    // get the incompleters user ID list
	    sql = "select distinct(invitee) from "
		    + s_id
		    + "_data as s, invitee as i where s.invitee=i.id and status IS NOT NULL order by invitee";

	    dbtype = stmt.execute(sql);
	    rs = stmt.getResultSet();
	    while (rs.next()) {
		incompleter_id += rs.getString(1) + " ";
	    }
	    sp_id[0] = nonresponder_id;
	    sp_id[1] = incompleter_id;

	} catch (Exception e) {
	    AdminInfo.log_error("ADMIN INFO - GET NONRESPONDERS INCOMPLETERS: "
		    + e.toString(), e);
	}
	return;
    }

    /**
     * Creates a CSV files.
     * 
     * @param filename
     * @return true if the CSV file is created & written successfully, otherwise
     *         it returns false.
     */
    public String buildCsvString(String filename) {

	// get the data table name
	String tname = filename.substring(0, filename.indexOf("."));
	Connection conn = null;
	Statement stmt = null;

	try {
	    // get database connection
	    conn = getDBConnection();
	    stmt = conn.createStatement();
	    String sql = "";

	    sql = "describe " + tname;
	    // log_error(sql);
	    stmt.execute(sql);
	    ResultSet rs = stmt.getResultSet();
	    String sqlm = "select ";
	    String output_str = "";
	    String[] field_name = new String[1000];
	    String[] delimitor = new String[1000];
	    int i = 0;
	    while (rs.next()) {

		field_name[i] = rs.getString("Field");
		output_str += "\"" + field_name[i] + "\",";
		sqlm += field_name[i] + ",";
		if (rs.getString("Type").indexOf("int") != -1
			|| rs.getString("Type").indexOf("decimal") != -1)
		    delimitor[i] = "";
		else
		    delimitor[i] = "\"";
		i++;
	    }

	    output_str = output_str.substring(0, output_str.length() - 1)
		    + "\n";
	    sqlm = sqlm.substring(0, sqlm.length() - 1) + " from " + tname;
	    // log_error(sqlm);
	    stmt.execute(sqlm);
	    rs = stmt.getResultSet();

	    while (rs.next()) {
		for (int j = 0; j < i; j++) {
		    String field_value = rs.getString(field_name[j]);
		    if (field_value == null
			    || field_value.equalsIgnoreCase("null")) {
			field_value = "";
		    }
		    if (field_value.indexOf("\"") != -1) {
			field_value = field_value.replaceAll("\"", "\"\"");
			log_info(field_value);
		    }
		    // if(field_value.equalsIgnoreCase(""))
		    // delimitor[j] = "";
		    output_str += delimitor[j] + field_value + delimitor[j]
			    + ",";
		}
		output_str = output_str.substring(0, output_str.length() - 1)
			+ "\n";
	    }

	    return output_str;
	} catch (SQLException e) {
	    log_error("ADMIN INFO - CREATE CSV FILE: " + e.toString(), e);
	    log.error("Database Error while download invitee list ", e);
	    return null;
	} finally {
	    if (stmt != null) {
		try {
		    stmt.close();
		} catch (SQLException e) {
		    log.error("SQL connection closing failed", e);
		}
	    }
	    if (conn != null) {
		try {
		    conn.close();
		} catch (SQLException e) {
		    log.error("SQL connection closing failed", e);
		}
	    }
	}
    }

    public String buildXmlCssSql(String filePath, String fileName) {

	InputStream fileInputStream = CommonUtils.loadResource(filePath
		+ fileName);
	StringBuffer strBuff = new StringBuffer();
	int ch;

	if (fileInputStream != null) {
	    try {
		while ((ch = fileInputStream.read()) != -1) {
		    strBuff.append(Character.valueOf((char) ch));
		}
	    } catch (IOException e) {
		log.error("I/O error occured", e);
		return strBuff.toString();
	    } finally {
		if (fileInputStream != null) {
		    try {
			fileInputStream.close();
		    } catch (IOException e) {
			log.error("I/O error occured", e);
			// That's Okie!
		    }
		}
	    }
	}
	return strBuff.toString();

    }

    public static String decode(String char_id) {
	String result = new String();
	int sum = 0;
	for (int i = char_id.length() - 1; i >= 0; i--) {
	    char c = char_id.charAt(i);
	    int remainder = c - 65;
	    sum = sum * 26 + remainder;
	}

	sum = sum - 97654;
	int remain = sum % 31;
	if (remain == 0) {
	    sum = sum / 31;
	    result = Integer.toString(sum);
	} else {
	    result = "invalid";
	}
	return result;
    }

    public static String encode(String user_id) {
	int base_numb = Integer.parseInt(user_id) * 31 + 97654;
	String s1 = Integer.toString(base_numb);
	String s2 = Integer.toString(26);
	BigInteger b1 = new BigInteger(s1);
	BigInteger b2 = new BigInteger(s2);

	int counter = 0;
	String char_id = new String();
	while (counter < 5) {
	    BigInteger[] bs = b1.divideAndRemainder(b2);
	    b1 = bs[0];
	    int encode_value = bs[1].intValue() + 65;
	    char_id = char_id + (new Character((char) encode_value).toString());
	    counter++;
	}
	return char_id;
    }

    /** print invite list of users for a study_space */

    public String print_invite() {
	String outputString = "";
	try {
	    Connection conn = this.getDBConnection();
	    Statement stmt = conn.createStatement();
	    Statement stmtm = conn.createStatement();
	    String sql = "SELECT id, firstname, lastname, salutation, email FROM invitee ORDER BY id";
	    boolean dbtype = stmt.execute(sql);
	    ResultSet rs = stmt.getResultSet();
	    outputString += "<table class=tth border=1 cellpadding=2 cellspacing=0 bgcolor=#FFFFF5>";
	    outputString += "<tr>";
	    outputString += "<th class=sfon></th>";
	    outputString += "<th class=sfon>User ID</th>";
	    outputString += "<th class=sfon>User Name</th>";
	    outputString += "<th class=sfon>User's Email Address</th></tr>";

	    while (rs.next()) {
		String sqlm = "select distinct(invitee) from consent_response where invitee not in (select invitee from consent_response where answer='Y') and invitee="
			+ rs.getString(1);
		boolean dbtypem = stmtm.execute(sqlm);
		ResultSet rsm = stmtm.getResultSet();
		if (rsm.next())
		    outputString += "<tr bgcolor='#E4E4E4'>";
		else
		    outputString += "<tr>";
		outputString += "<td><input type='checkbox' name='user' value='"
			+ rs.getString(1) + "'></td>";
		outputString += "<td>" + rs.getString(1) + "</td>";
		outputString += "<td>" + rs.getString(4) + " "
			+ rs.getString(2) + " " + rs.getString(3) + "</td>";
		outputString += "<td>" + rs.getString(5) + "</td>";
		outputString += "</tr>";
	    }
	    rs.close();
	    outputString += "</table>";
	    conn.close();
	} catch (Exception e) {
	    log_error("ADMIN INFO - PRINT INVITE: " + e.toString(), e);
	}
	return outputString;
    }

    /** return HTML showing counts of users in each state */
    public String get_user_counts_in_states(String survey_id) {
	String outputString = "";
	// Hashtable states_counts = new Hashtable();
	int n_not_invited = 0, n_invited = 0, n_declined = 0, n_started = 0, n_start_reminded = 0;
	int n_not_responded = 0, n_interrupted = 0, n_complete_reminded = 0, n_not_completed = 0, n_completed = 0;
	int n_all = 0;

	try {
	    // connect to the database
	    Connection conn = getDBConnection();
	    Statement stmt = conn.createStatement();
	    outputString += "<table border=0>";

	    String sql = "select count(distinct id) as uninvited from invitee where id not in (select invitee from survey_user_state where survey='"
		    + survey_id + "')";
	    stmt.execute(sql);
	    ResultSet rs = stmt.getResultSet();
	    while (rs.next()) {
		n_not_invited = rs.getInt("uninvited");
	    }
	    n_all += n_not_invited;

	    sql = "select count(distinct invitee) as counts, state from survey_user_state where survey='"
		    + survey_id + "' group by state order by state";
	    stmt.execute(sql);
	    rs = stmt.getResultSet();
	    while (rs.next()) {
		if (rs.getString("state").equalsIgnoreCase("invited")) {
		    n_invited = rs.getInt("counts");
		    n_all += n_invited;
		}
		if (rs.getString("state").equalsIgnoreCase("declined")) {
		    n_declined = rs.getInt("counts");
		    n_all += n_declined;
		}
		if (rs.getString("state").equalsIgnoreCase("started")) {
		    n_started = rs.getInt("counts");
		    n_all += n_started;
		}
		if (rs.getString("state").equalsIgnoreCase("interrupted")) {
		    n_interrupted = rs.getInt("counts");
		    n_all += n_interrupted;
		}
		if (rs.getString("state").indexOf("start_reminder") != -1) {
		    n_start_reminded += rs.getInt("counts");
		    n_all += n_start_reminded;
		}
		if (rs.getString("state").equalsIgnoreCase("non_responder")) {
		    n_not_responded = rs.getInt("counts");
		    n_all += n_not_responded;
		}
		if (rs.getString("state").indexOf("completion_reminder") != -1) {
		    n_complete_reminded += rs.getInt("counts");
		    n_all += n_complete_reminded;
		}
		if (rs.getString("state").equalsIgnoreCase("incompleter")) {
		    n_not_completed = rs.getInt("counts");
		    n_all += n_not_completed;
		}
		if (rs.getString("state").equalsIgnoreCase("completed")) {
		    n_completed = rs.getInt("counts");
		    n_all += n_completed;
		}
	    }

	    outputString += "<tr><td><p class=\"status\">All</p></td><td align=center><a href='show_people.jsp?s="
		    + survey_id + "&st=all'>" + n_all + "</a></td></tr>";
	    outputString += "<tr><td><p class=\"status\">Not Invited</p></td><td align=center><a href='show_people.jsp?s="
		    + survey_id
		    + "&st=not_invited'>"
		    + n_not_invited
		    + "</td></tr>";

	    outputString += "<tr><td><p class=\"status-category\"><u>Not Started</u></p></td><td></td></tr>";
	    outputString += "<tr><td><p class=\"status\">Invited</p></td><td align=center><a href='show_people.jsp?s="
		    + survey_id
		    + "&st=invited'>"
		    + n_invited
		    + "</a></td></tr>";
	    outputString += "<tr><td><p class=\"status\">Reminder Sent</p></td><td align=center><a href='show_people.jsp?s="
		    + survey_id
		    + "&st=start_reminder'>"
		    + n_start_reminded
		    + "</a></td></tr>";

	    outputString += "<tr><td><p class=\"status-category\"><u>Incomplete</u></p></td><td/></tr>";
	    outputString += "<tr><td><p class=\"status\">Currently Taking</p></td><td align=center><a href='show_people.jsp?s="
		    + survey_id
		    + "&st=started'>"
		    + n_started
		    + "</a></td></tr>";
	    outputString += "<tr><td><p class=\"status\">Interrupted</p></td><td align=center><a href='show_people.jsp?s="
		    + survey_id
		    + "&st=interrupted'>"
		    + n_interrupted
		    + "</a></td></tr>";
	    outputString += "<tr><td><p class=\"status\">Reminder Sent</p></td><td align=center><a href='show_people.jsp?s="
		    + survey_id
		    + "&st=completion_reminder'>"
		    + n_complete_reminded + "</a></td></tr>";

	    outputString += "<tr><td><p class=\"status-category\"><u>End States</u></p></td><td/></tr>";
	    outputString += "<tr><td><p class=\"status\">Completed</p></td><td align=center><a href='show_people.jsp?s="
		    + survey_id
		    + "&st=completed'>"
		    + n_completed
		    + "</a></td></tr>";
	    outputString += "<tr><td><p class=\"status\">Incompleter</p></td><td align=center><a href='show_people.jsp?s="
		    + survey_id
		    + "&st=incompleter'>"
		    + n_not_completed
		    + "</a></td></tr>";
	    outputString += "<tr><td><p class=\"status\">Nonresponder</p></td><td align=center><a href='show_people.jsp?s="
		    + survey_id
		    + "&st=non_responder'>"
		    + n_not_responded
		    + "</a></td></tr>";
	    outputString += "<tr><td><p class=\"status\">Declined</p></td><td align=center><a href='show_people.jsp?s="
		    + survey_id
		    + "&st=declined'>"
		    + n_declined
		    + "</a></td></tr>";

	    outputString += "</table>";
	    rs.close();
	    stmt.close();
	    conn.close();
	} catch (Exception e) {
	    log_error(
		    "ADMIN INFO - GET USER COUNTS IN STATES: " + e.toString(),
		    e);
	}
	return outputString;
    }

    /** print the user groups identified by their states */
    public String print_user_state(String state, String survey_id) {
	String outputString = "";
	try {
	    // connect to the database
	    Connection conn = getDBConnection();
	    Statement stmt = conn.createStatement();
	    String sql = "";

	    if (state.equalsIgnoreCase("not_invited")) {

		outputString += "<tr><td class=sfon align=center>ID</td>"
			+ "<td class=sfon align=center>Name</td>"
			+ "<td class=sfon align=center>Email Address</td></tr>";

		sql = "select id, firstname, lastname, AES_DECRYPT(email,'"
			+ this.myStudySpace.db.email_encryption_key
			+ "') as email from invitee where id not in (select invitee from survey_user_state where survey='"
			+ survey_id + "')";
		boolean dbtype = stmt.execute(sql);
		ResultSet rs = stmt.getResultSet();
		while (rs.next()) {
		    outputString += "<tr><td align=center>"
			    + rs.getString("id") + "</td>";
		    outputString += "<td align=center>"
			    + rs.getString("firstname") + " "
			    + rs.getString("lastname") + "</td>";
		    outputString += "<td align=center>" + rs.getString("email")
			    + "</td></tr>";
		}
	    } else if (state.equalsIgnoreCase("all")) {
		// all users who have been invited
		outputString += "<tr><td class=sfon align=center>ID</td>"
			+ "<td class=sfon align=center>Name</td>"
			+ "</td><td class=sfon align=center>State</td>"
			+ "<td class=sfon align=center>Email</td>";
		sql = "select i.id, i.firstname, i.lastname, AES_DECRYPT(i.email, '"
			+ this.myStudySpace.db.email_encryption_key
			+ "') as email, u.state as state "
			+ "from invitee as i, survey_user_state as u "
			+ "where i.id=u.invitee and u.survey='"
			+ survey_id
			+ "' order by i.id";
		boolean dbtype = stmt.execute(sql);
		ResultSet rs = stmt.getResultSet();
		String user_id = "";
		while (rs.next()) {
		    outputString += "<tr><td align=center>"
			    + rs.getString("id") + "</td>";
		    outputString += "<td align=center>"
			    + rs.getString("firstname") + " "
			    + rs.getString("lastname") + "</td>";
		    outputString += "<td align=center>" + rs.getString("state")
			    + "</td>";
		    outputString += "<td align=center>" + rs.getString("email")
			    + "</td></tr>";
		}
		// all users who have not been invited
		sql = "select id, firstname, lastname, AES_DECRYPT(email,'"
			+ this.myStudySpace.db.email_encryption_key
			+ "') as email from invitee where id not in (select invitee from survey_user_state where survey='"
			+ survey_id + "')";
		dbtype = stmt.execute(sql);
		rs = stmt.getResultSet();
		while (rs.next()) {
		    outputString += "<tr><td align=center>"
			    + rs.getString("id") + "</td>";
		    outputString += "<td align=center>"
			    + rs.getString("firstname") + " "
			    + rs.getString("lastname") + "</td>";
		    outputString += "<td align=center>" + "Not Invited"
			    + "</td>";
		    outputString += "<td align=center>" + rs.getString("email")
			    + "</td></tr>";
		}
	    } else {
		outputString += "<tr><td class=sfon align=center>ID</td>"
			+ "<td class=sfon align=center>Name</td>"
			+ "</td><td class=sfon align=center>State</td>"
			+ "<td class=sfon align=center>Entry Time</td>"
			+ "<td class=sfon align=center>Email</td>"
			+ "<td class=sfon align=center>Messages (Sent Time)";
		sql = "select i.id, firstname, lastname, AES_DECRYPT(email, '"
			+ this.myStudySpace.db.email_encryption_key
			+ "') as email, state, entry_time, message, sent_date "
			+ "from invitee as i, survey_message_use as m, survey_user_state as u "
			+ "where i.id = m.invitee and i.id=u.invitee and m.survey=u.survey and u.survey='"
			+ survey_id + "' " + "and state like '" + state
			+ "%' order by i.id";

		boolean dbtype = stmt.execute(sql);
		ResultSet rs = stmt.getResultSet();
		String user_id = "", last_user_id = "";
		while (rs.next()) {
		    user_id = rs.getString("id");
		    if (!user_id.equalsIgnoreCase(last_user_id)) {
			last_user_id = user_id;
			// print out the new row
			outputString += "</td></tr><tr><td align=center>"
				+ user_id + "</td>";
			outputString += "<td align=center>"
				+ rs.getString("firstname") + " "
				+ rs.getString("lastname") + "</td>";
			outputString += "<td align=center>"
				+ rs.getString("state") + "</td>";
			outputString += "<td align=center>"
				+ rs.getString("entry_time") + "</td>";
			outputString += "<td align=center>"
				+ rs.getString("email") + "</td>";
			outputString += "<td align=center>"
				+ rs.getString("message") + " "
				+ rs.getString("sent_date");
		    } else {
			// append other messages under the same invitee ID
			outputString += "<br>" + rs.getString("message") + " "
				+ rs.getString("sent_date");
		    }
		}
		outputString += "</td></tr>";
	    }

	    stmt.close();
	    conn.close();
	} catch (Exception e) {
	    log_error("ADMIN INFO - PRINT USER STATE: " + e.toString(), e);
	}
	return outputString;
    }

    public String make_remoteURL(String file_type, String study_name) {
	String url_str = myStudySpace.servlet_urlRoot
		+ WiseConstants.SURVEY_APP + "/" + "admin_" + file_type
		+ "_loader" + "?SID=" + study_id + "&SurveyName=" + study_name;
	return url_str;
    }

    // call the "loader" servlet (survey_loader, preface_loader, etc.) to notify
    // remote
    // Surveyor Application that a survey or some other file has changed and
    // needs to be flushed and reread
    // Not using on Ansari because Apache seems to be blocking the local call
    public String load_remote(String file_type, String study_name) {
	String url_str = make_remoteURL(file_type, study_name);
	String upload_result = "";
	URL url = null;
	BufferedReader in = null;
	String current_line = null;

	try {
	    url = new URL(url_str);
	    in = new BufferedReader(new InputStreamReader(url.openStream()));
	    while ((current_line = in.readLine()) != null) {
		upload_result += current_line;
	    }
	} catch (IOException e) {
	    log.error("Reader failed to read ", e);
	    log_error("Wise error: Remote " + file_type + " load error after"
		    + upload_result + ": " + e.toString(), e);
	} finally {
	    if (in != null) {
		try {
		    in.close();
		} catch (IOException e) {
		    // That's okie!
		    log.error("Reader Stream close failure ", e);
		}
	    }

	}
	return upload_result;
    }

    // Return the complete URL to the servlet root directory for the application
    // administering the survey
    public String getStudyServerPath() {
	return myStudySpace.servlet_urlRoot;
    }

    /* Clears, drops, or archives survey data depending on survey's status */
    // D - Clear submitted data from surveys in Development mode
    // R - Remove entire survey in Development mode
    // P - clean up and archive the data of surveys in production mode
    // TODO: READ the databank at call time rather than relying on JSP tool to
    // know proper status
    public String clearSurvey(String survey_id, String survey_status) {
	if (survey_id == null || study_id == null || survey_status == null)
	    return "<p align=center>SURVEY clear ERROR: can't get the survey id/status or study id </p>";
	Data_Bank db = myStudySpace.db;
	Survey survey = myStudySpace.get_Survey(survey_id);
	if (survey_status.equalsIgnoreCase("D"))
	    return db.clear_surveyData(survey);
	else if (survey_status.equalsIgnoreCase("R"))
	    return db.delete_survey(survey);
	else if (survey_status.equalsIgnoreCase("P"))
	    return db.archive_prodSurvey(survey);
	return "Unrecognized Survey Status/Type";
    }

    /**
     * @return the loginAttemptNumbers
     */
    public static ConcurrentHashMap<String, Integer> getLoginAttemptNumbers() {
	return loginAttemptNumbers;
    }

    /**
     * @return the lastlogintime
     */
    public static ConcurrentHashMap<String, Long> getLastlogintime() {
	return lastLoginTime;
    }

}
