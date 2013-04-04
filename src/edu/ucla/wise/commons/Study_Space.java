package edu.ucla.wise.commons;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.google.common.base.Strings;

import edu.ucla.wise.initializer.StudySpaceParametersProvider;
import edu.ucla.wise.studyspace.parameters.StudySpaceParameters;

/**
 * Study space is the core of WISE system -- represents the core abstractions
 * for individual survey projects
 */

public class Study_Space {

    private static Logger log = Logger.getLogger(Study_Space.class);

    /** CLASS STATIC VARIABLES */
    private static Hashtable<String, Study_Space> ALL_SPACES; // contains actual
							     // study spaces
							     // indexed
					// by name
    private static Hashtable<String, String> SPACE_names; // contains index of
							  // all study names
					  // in the properties file by ID
    // NOTE: Properties are read once at startup, therefore must restart server
    // if a Study Space is added

    // public static String xml_loc;

    public static String font = "<font face='Verdana, Arial, Helvetica, sans-serif' size='-1'>";

    /** INSTANCE VARIABLES */
    public Hashtable<String, Survey> surveys;
    public Preface preface;

    public String id; // the study_space's number, which can be encoded
    public String study_name;
    public String title;

    // DIRECTORIES AND PATHS
    public String server_url;
    public String dir_name;
    private String prefacePath;
    private String application;
    public String app_urlRoot, servlet_urlRoot;
    public String sharedFile_urlRoot, style_url, image_url;
    public String emailSendingTime;

    public Data_Bank db; // one DB per SS

    /** CLASS FUNCTIONS */

    /** static initializer */
    static {
	ALL_SPACES = new Hashtable<String, Study_Space>();
	SPACE_names = new Hashtable<String, String>();
	// better not to parse all ss's in advance
	// Load_Study_Spaces();
    }

    public static void setupStudies() {
	Data_Bank.SetupDB(WISE_Application.sharedProps);
	// Just read the names of all unique Studies and save the name:ID pairs
	// in a hash for quicker lookup later
	// note when called by a reload, does not drop already-parsed studies
	// but does reread props file to enable load of new studies
	// TODO (low): consider a private "stub" class to hold all values from
	// props file without parsing XML file
	
	Map<String,StudySpaceParameters> allSpaceParams = StudySpaceParametersProvider.getInstance().getStudySpaceParametersMap();
	
	Iterator<String> allSpaceParamsItr = allSpaceParams.keySet().iterator();

	while (allSpaceParamsItr.hasNext()) {
	    String spaceName = allSpaceParamsItr.next();
	    SPACE_names.put(allSpaceParams.get(spaceName).getId(), spaceName);
	}
	log.info("study space setup complete");
	/*
	Enumeration enu = WISE_Application.sharedProps.getKeys();
	while (enu.hasMoreElements()) {
	    String key = (String) enu.nextElement();
	    if (key.indexOf(".studyid") != -1) // pull out just the study ID
					       // properties
	    {
		String idNum = WISE_Application.sharedProps.getString(key);
		String study_name = key.substring(0, key.indexOf(".studyid"));
		SPACE_names.put(idNum, study_name);
	    }
	}
	
	*/
    }

    /** search by the numeric study ID and return the Study_Space instance */
    public static Study_Space get_Space(String studyID) {
	if (SPACE_names == null || ALL_SPACES == null)
	    WISE_Application.log_error(
		    "GET Study Space failure - hash uninitialized. Try server restart on "
			    + WISE_Application.rootURL + ", "
			    + Surveyor_Application.ApplicationName, null);
	Study_Space ss = ALL_SPACES.get(studyID);
	if (ss == null) {
	    String sName = SPACE_names.get(studyID);
	    if (sName != null) {
		ss = new Study_Space(sName);
		// put Study_Space in ALL_SPACES
		ALL_SPACES.put(ss.id, ss);
	    }

	}
	return ss;
    }

    /** Load all the Study_Space spaces applicable for the given local server */
    public static String Load_Study_Spaces() {
	String spaceName = "";
	String resultstr = "";
	try {
	    if (SPACE_names == null || SPACE_names.size() < 1)
		return "Error: No Study Spaces found in props file";

	    Map<String, StudySpaceParameters> allSpaceParams = StudySpaceParametersProvider
		    .getInstance().getStudySpaceParametersMap();

	    log.info("There are " + allSpaceParams.size()
		    + " StudySpaceParameters objects");

	    Iterator<String> allSpaceNameItr = allSpaceParams.keySet()
		    .iterator();

	    while (allSpaceNameItr.hasNext()) {

		spaceName = allSpaceNameItr.next();
		
		String studySvr = allSpaceParams.get(spaceName).getServerUrl();
		String studyApp = allSpaceParams.get(spaceName)
			.getServerApplication();
		
		if (studySvr.equalsIgnoreCase(WISE_Application.rootURL)
			&& studyApp
				.equalsIgnoreCase(Surveyor_Application.ApplicationName)
			&& !Strings.isNullOrEmpty(spaceName)) {
		    // create new Study_Space
		    Study_Space ss = new Study_Space(spaceName);
		    // put Study_Space in ALL_SPACES
		    ALL_SPACES.put(ss.id, ss);
		    resultstr += "Loaded Study Space: " + ss.id + " for user "
			    + ss.db.dbuser + " <BR>\n";
		}

	    }

	    /*
	     * 20dec // get study space info from shared properties Enumeration
	     * enu = SPACE_names.keys(); while (enu.hasMoreElements()) { studyID
	     * = (String) enu.nextElement(); study_name =
	     * SPACE_names.get(studyID); String studySvr =
	     * WISE_Application.sharedProps .getString(study_name + ".server");
	     * String studyApp = WISE_Application.sharedProps
	     * .getString(study_name + ".serverApp"); if
	     * (studySvr.equalsIgnoreCase(WISE_Application.rootURL) && studyApp
	     * .equalsIgnoreCase(Surveyor_Application.ApplicationName) &&
	     * study_name != null && !study_name.equals("")) { // create new
	     * Study_Space Study_Space ss = new Study_Space(study_name); // put
	     * Study_Space in ALL_SPACES ALL_SPACES.put(ss.id, ss); resultstr +=
	     * "Loaded Study Space: " + ss.id + " for user " + ss.db.dbuser +
	     * " <BR>\n"; } }
	     */
	} catch (Exception e) {
	    log.error("Load Study Spaces Error for  name " + spaceName, e);
	}
	return resultstr;
    }

    /**
     * constructor to create study space and initialize the surveys & messages
     * hashtables
     */
    public Study_Space(String studyName) {
	if (studyName == null || studyName.equals("")) // will still return an
						       // uninitialized
						       // instance
	    return;
	study_name = studyName;
	String filename = "";

	StudySpaceParameters spaceParams = StudySpaceParametersProvider
		.getInstance().getStudySpaceParameters(studyName);

	try {

	    db = new Data_Bank(this); // one DB per SS

	    // Construct instance variables for this particular study space
	    id = spaceParams.getId();
	    // 20dec id = WISE_Application.sharedProps.getString(studyName +
	    // ".studyid");
	    title = spaceParams.getProjectTitle();
	    // 20dec title = WISE_Application.sharedProps.getString(studyName
	    // + ".proj.title");

	    // SET UP all of the paths that will apply for this Study Space,
	    // regardless of the app instantiating it
	    server_url = spaceParams.getServerUrl();
	    // 20dec server_url =
	    // WISE_Application.sharedProps.getString(studyName
	    // + ".server");
	    String dir_in_props = spaceParams.getFolderName();
	    // 20decString dir_in_props = WISE_Application.sharedProps
	    // .getString(studyName + ".dirName");
	    if (dir_in_props == null)
		dir_name = study_name; // default
	    else
		dir_name = dir_in_props;
	    application = spaceParams.getServerApplication();

	    emailSendingTime = spaceParams.getEmailSendingTime();
	    // 20dec application =
	    // WISE_Application.sharedProps.getString(studyName
	    // + ".serverApp");
	    app_urlRoot = server_url + "/" + application + "/";
	    // Manoj changes
	    // servlet_urlRoot = server_url + "/"+ application + "/servlet/";
	    servlet_urlRoot = server_url + "/" + application + "/";

	    sharedFile_urlRoot = app_urlRoot
		    + spaceParams.getSharedFiles_linkName() + "/";

	    // 20dec sharedFile_urlRoot = app_urlRoot
	    // + WISE_Application.sharedProps.getString(studyName
	    // + ".sharedFiles_linkName") + "/";

	    // project-specific styles and images need to be in shared area so
	    // they can be uploaded by admin server
	    style_url = sharedFile_urlRoot + "style/" + dir_name + "/";
	    image_url = sharedFile_urlRoot + "images/" + dir_name + "/";
	    // create & initialize the Preface
	    prefacePath = Surveyor_Application.xml_loc + "/" + dir_name
		    + "/preface.xml";
	    load_preface();
	    // create the message sender
	    surveys = new Hashtable<String, Survey>();
	    db.readSurveys();
	} catch (Exception e) {
	    WISE_Application.log_error("Study Space create failure: " + id
		    + " at survey : " + filename + ". Error: " + e, e);
	}
    }

    public static Study_Space[] get_all() {
	int n_spaces = ALL_SPACES.size();
	log.info("There are " + n_spaces + " Study Spaces");
	if (n_spaces < 1) {
	    Load_Study_Spaces();
	    n_spaces = ALL_SPACES.size();
	    log.info("Loaded " + n_spaces + " study spaces");
	}
	Study_Space[] result = new Study_Space[n_spaces];
	Enumeration<Study_Space> et = Study_Space.ALL_SPACES.elements();
	int i = 0;
	while (et.hasMoreElements() && i < n_spaces) {
	    result[i++] = et.nextElement();
	}
	return result;
    }

    /** deconstructor to destroy the surveys and messages hashtables */
    public void destroy() {
	surveys = null;
    }

    /** INSTANCE FUNCTIONS */

    /** establish dbase connection and returns a Connection object */
    public Connection getDBConnection() throws SQLException {
	// return
	// DriverManager.getConnection(mysql_url+dbdata+"?user="+dbuser+"&password="+dbpwd);
	// return
	// DriverManager.getConnection("jdbc:mysql://"+mysql_server+"/"+dbdata+"?user="+dbuser+"&password="+dbpwd);
	return db.getDBConnection();
    }

    public Data_Bank getDB() {
	return db;
    }

    /** search by the survey ID and returns a specific survey */
    public Survey get_Survey(String survey_id) {
	Survey s = surveys.get(survey_id);
	return s;
    }

    /**
     * load or reload a survey from file, return survey ID or null if
     * unsuccessful
     */
    public String load_survey(String filename) {
	String sid = null;
	Survey s;
	try {
	    String file_loc = Surveyor_Application.xml_loc
		    + System.getProperty("file.separator") + dir_name
		    + System.getProperty("file.separator") + filename;
	    DocumentBuilderFactory factory = DocumentBuilderFactory
		    .newInstance();
	    factory.setCoalescing(true);
	    factory.setExpandEntityReferences(false);
	    factory.setIgnoringComments(true);
	    factory.setIgnoringElementContentWhitespace(true);

	    /*
	     * Document xml_doc = factory.newDocumentBuilder().parse(
	     * CommonUtils.loadResource(file_loc));
	     */

	    log.info("Fetching survey file " + filename + " from database for "
		    + study_name);
	    InputStream surveyFileInputStream = db.getXmlFileFromDatabase(
		    filename, study_name);

	    if (surveyFileInputStream == null) {
		throw new FileNotFoundException();
	    }

	    Document xml_doc = factory.newDocumentBuilder().parse(
		    surveyFileInputStream);

	    s = new Survey(xml_doc, this);
	    if (s != null) {
		sid = s.id;
		surveys.put(sid, s);
	    }

	} catch (FileNotFoundException e) {
	    log.error("Study Space " + dir_name
		    + " failed to parse survey " + filename + ". Error: " + e,
		    e);
	}
 catch (SAXException e) {
	    log.error("Study Space " + dir_name + " failed to parse survey "
		    + filename + ". Error: " + e, e);
	} catch (ParserConfigurationException e) {
	    log.error("Study Space " + dir_name + " failed to parse survey "
		    + filename + ". Error: " + e, e);
	} catch (IOException e) {
	    log.error("Study Space " + dir_name + " failed to parse survey "
		    + filename + ". Error: " + e, e);
	}
	return sid;
	
    }

    public void drop_Survey(String survey_id) {
	surveys.remove(survey_id);
    }

    /** load the preface file */
    // TODO: check admin; call when new preface uploaded
    public boolean load_preface() {
	    preface = new Preface(this, "preface.xml");
	if (preface == null) {
	    return false;
	}
	    preface.setHrefs(servlet_urlRoot, image_url);
	    return true;
    }

    /** get a preface */
    public Preface get_preface() {
	if (preface == null) // should happen only if there's been some major
			     // problem
	    if (!load_preface()) {
		WISE_Application.log_info("Study Space " + dir_name
			+ " failed to load its preface file ");
		return null;
	    }
	return preface;
    }

    public User get_User(String msg_id) {
	return db.makeUser_fromMsgID(msg_id);
    }

    public String sendInviteReturnDisplayMessage(String msg_type,
	    String message_seq_id, String survey_id, String whereStr,
	    boolean isReminder) {
	return send_messages(msg_type, message_seq_id, survey_id, whereStr,
		isReminder, true);
    }

    public String sendInviteReturnMsgSeqId(String msg_type,
	    String message_seq_id, String survey_id, String whereStr,
	    boolean isReminder) {
	return send_messages(msg_type, message_seq_id, survey_id, whereStr,
		isReminder, false);
    }

    // send message to all invitees who match on whereStr
    private String send_messages(String msg_type, String message_seq_id,
	    String survey_id, String whereStr, boolean isReminder,
	    boolean displayMessage) {

	String messageSequenceId = null;
	// look up the correct message sequence in preface
	Message_Sequence msg_seq = this.preface
		.get_message_sequence(message_seq_id);
	if (msg_seq == null) {
	    AdminInfo
		    .log_info("ADMIN INFO - SEND MESSAGES: Can't get the requested  message sequence "
			    + message_seq_id + AdminInfo.class.getSimpleName());
	    return null;
	}
	Message msg = msg_seq.get_type_message(msg_type); // passes thru an
	// integer for
	// 'other'
	// messages
	if (msg == null) {
	    AdminInfo
		    .log_info("ADMIN INFO - SEND MESSAGES: Can't get the message from hash");
	    return null;
	}
	String outputString = "";
	Message_Sender sender = new Message_Sender(msg_seq);
	try {
	    Connection conn = getDBConnection();
	    Statement msgUseQry = conn.createStatement();
	    Statement inviteeQuery = conn.createStatement();
	    Statement usrSteQry = conn.createStatement();

	    String messageId = org.apache.commons.lang3.RandomStringUtils
		    .randomAlphanumeric(22);

	    // FIRST, to obtain new IDs, insert pending message_use records
	    // for
	    // each subject
	    String msgUse_sql = "INSERT INTO survey_message_use (messageId,invitee, survey, message) "
		    + "SELECT '"
		    + messageId
		    + "', id, '"
		    + survey_id
		    + "', 'attempt' FROM invitee WHERE " + whereStr;
	    msgUseQry.execute(msgUse_sql);

	    List<String> success_ids = new ArrayList<String>();
	    outputString += "Sending message '" + msg.subject + "' to:<p>";

	    // Now get back newly-created message IDs and invitee data at
	    // the
	    // same time
	    String invitee_sql = "SELECT firstname, lastname, salutation, AES_DECRYPT(email,'"
		    + this.db.email_encryption_key
		    + "'), invitee.id, survey_message_use.messageId "
		    + "FROM invitee, survey_message_use WHERE invitee.id = survey_message_use.invitee "
		    + "AND message = 'attempt' AND survey = '"
		    + survey_id
		    + "' AND " + whereStr;
	    ResultSet rs = inviteeQuery.executeQuery(invitee_sql);

	    // send email message to each selected invitee
	    while (rs.next()) {
		String firstname = rs.getString(1);
		String lastname = rs.getString(2);
		String salutation = rs.getString(3);
		String email = rs.getString(4);
		String invitee_id = rs.getString(5);
		String message_id = rs.getString(6);
		// This is used when for anonymous user. We want to return the
		// message id to the calling function from save_anno_user so
		// that it can forward the survey request automatically.
		messageSequenceId = message_id;
		// print out the user information
		outputString += salutation + " " + firstname + " " + lastname
			+ " with email address &lt;" + email + "&gt; -&gt; ";
		String msg_result = sender.send_message(msg, message_id, email,
			salutation, lastname, this.id);

		if (msg_result.equalsIgnoreCase("")) {
		    outputString += "message sent.<br>";
		    success_ids.add(invitee_id);
		} else {
		    msgUse_sql = "UPDATE survey_message_use SET message= 'err:"
			    + msg_result
			    + "' WHERE message = 'attempt' AND survey = '"
			    + survey_id + "' AND invitee = " + invitee_id;
		    msgUseQry.execute(msgUse_sql);
		    outputString += msg_result + "<br><br>";
		}
		String state = msg_result.equalsIgnoreCase("") ? "invited"
			: "email_error";
		if (msg_type.equalsIgnoreCase("invite")) {
		    String sql_u = "insert into survey_user_state (invitee, state, survey, message_sequence) "
			    + "values("
			    + invitee_id
			    + ", '"
			    + state
			    + "', '"
			    + survey_id
			    + "', '"
			    + message_seq_id
			    + "') "
			    + "ON DUPLICATE KEY UPDATE state='"
			    + state
			    + "', state_count=1, message_sequence=VALUES(message_sequence)";
		    // note timestamp updates automatically
		    usrSteQry.execute(sql_u);
		}
	    }
	    if (success_ids.size() > 0) {
		String successLst = "(";
		for (int i = 0; i < (success_ids.size() - 1); i++) {
		    successLst += success_ids.get(i) + ",";
		}
		successLst += success_ids.get(success_ids.size() - 1) + ")";
		outputString += successLst + "<br><br>";
		// Update survey message use with successes
		msgUse_sql = "UPDATE survey_message_use SET message= '"
			+ msg.id + "' WHERE message = 'attempt' AND survey = '"
			+ survey_id + "' AND invitee in " + successLst;
		msgUseQry.execute(msgUse_sql);
	    }
	    conn.close();
	} catch (Exception e) {
	    AdminInfo.log_error("ADMIN INFO - SEND MESSAGES: " + e.toString(),
		    e);
	}
	// If the call comes from UI, we return outputString, if the call comes
	// from the anno user trying to take the survey we return messageSeqid
	// to the caller.
	return displayMessage ? outputString : messageSequenceId;
    }

    /** parse the config file and load all the study spaces */
    /*
     * public static void load_all_study_spaces() { try {
     * DriverManager.registerDriver(new com.mysql.jdbc.Driver());
     * 
     * // Get parser and an XML document Document doc =
     * DocumentBuilderFactory.newInstance
     * ().newDocumentBuilder().parse(config_loc);
     * 
     * // parse all study elements in the config file NodeList nl =
     * doc.getElementsByTagName("Study"); for (int i = 0; i < nl.getLength();
     * i++) { // create new Study_Space Study_Space ss = new
     * Study_Space(nl.item(i)); // put Study_Space in ALL_SPACES
     * ALL_SPACES.put(ss.id,ss); }
     * 
     * } catch (Exception e) {
     * Study_Util.email_alert("WISE - STUDY SPACE - LOAD ALL STUDY SPACES: "
     * +e.toString()); return; }
     * 
     * }
     */
    /** prints all the study spaces */
    /*
     * public static String print_ALL() { Study_Space ss;
     * 
     * String s = "ALL Study Spaces:<p>"; Enumeration e1 =
     * ALL_SPACES.elements(); while (e1.hasMoreElements()) { ss = (Study_Space)
     * e1.nextElement(); s += ss.print(); } s += "<p>"; return s; }
     */
    /** look up if the user and password exists in the list of study spaces */
    /*
     * public static String lookup_study_space(String u, String p) { Study_Space
     * ss; Enumeration e1 = ALL_SPACES.elements(); while (e1.hasMoreElements())
     * { ss = (Study_Space) e1.nextElement(); if (ss.dbuser.equalsIgnoreCase(u))
     * if (ss.dbpwd.equalsIgnoreCase(p)) return ss.id; } return null; }
     */

    /** returns if a specific Study_Space has been loaded */
    /*
     * public static boolean space_exists(String id) { Study_Space ss =
     * (Study_Space) ALL_SPACES.get(id); if (ss == null) return false; else
     * return true; }
     * 
     * /** constructor to initialize the surveys and messages hashtables
     */
    /*
     * public Study_Space(Node n) { try { // parse the config node id =
     * n.getAttributes().getNamedItem("ID").getNodeValue(); location =
     * n.getAttributes().getNamedItem("Location").getNodeValue(); dbdata =
     * n.getAttributes().getNamedItem("DB_Data").getNodeValue(); dbuser =
     * n.getAttributes().getNamedItem("DB_User").getNodeValue(); dbpwd =
     * n.getAttributes().getNamedItem("DB_Password").getNodeValue(); title =
     * n.getAttributes().getNamedItem("Title").getNodeValue();;
     * 
     * style_path = "/wise_test/file/style/" + location + "/";
     * 
     * 
     * msg = new Message(xml_loc + "/" + location + "/messages.xml", this);
     * if(msg==null) Study_Util.email_alert("study space msg can't be created");
     * 
     * // open database connection Connection conn = getDBConnection();
     * Statement stmt = conn.createStatement();
     * 
     * // load all the surveys surveys = new Hashtable(); String sql =
     * "SELECT filename from surveys, (select max(internal_id) as maxint from surveys group by id) maxes where maxes.maxint = surveys.internal_id"
     * ; boolean dbtype = stmt.execute(sql); ResultSet rs = stmt.getResultSet();
     * while (rs.next()) { String filename = rs.getString("filename"); Survey s
     * = new Survey(filename,this); surveys.put(s.id,s); }
     * 
     * // close database stmt.close(); conn.close(); } catch (Exception e) {
     * Study_Util
     * .email_alert("WISE - STUDY SPACE - CONSTRUCTOR: "+e.toString()); return;
     * } }
     */

    /** prints a specific study space */

    @Override
    public String toString() {
	String s = "STUDY SPACE<br>";
	s += "ID: " + id + "<br>";
	s += "Location: " + dir_name + "<br>";
	s += "Study Name: " + study_name + "<br>";
	// s += "DB Password: "+dbpwd+"<p>";

	// print surveys
	s += "<hr>SURVEYS<BR>";
	Survey svy;
	Enumeration<Survey> e1 = surveys.elements();
	while (e1.hasMoreElements()) {
	    svy = e1.nextElement();
	    s += svy.toString();
	}

	s += "<hr>PREFACE<BR>";
	s += preface.toString();
	return s;
    }

}
