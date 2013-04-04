package edu.ucla.wise.commons;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This class represents our email prompts
 */

public class Message {
    /** Email Image File Names */
    private static final String headerImgFilename = "email_header_img.jpg";
    private static final String footerImgFilename1 = "email_bottom_img1.jpg";
    private static final String footerImgFilename2 = "email_bottom_img2.jpg";
    private static final String WISE_SHARED = "WiseShared";
    private static final String htmlOpen = "<html><head><meta http-equiv='Content-Type' content='text/html; charset=iso-8859-1'></head>"
	    + "<body bgcolor=#FFFFFF text=#000000><center>";
    private static final String htmlClose = "</center></body></html>";

    /** Instance Variables */
    public String id, subject;
    public String main_body, html_body, html_header, html_tail, html_signature;
    private boolean html_format = false;
    public boolean has_link = false, has_dlink = false;
    public String msg_ref = null;
    private String servlet_path = null;

    // TODO: should ideally be customizable for survey/irb; should use xlst

    // the constructor for Message
    public Message(Node n) {
	try {
	    // get the message sequence
	    // message_sequence = msg_seq;
	    // get the message type
	    // type = message_type;
	    // parse out the reminder attributes: ID, subject, format, trigger
	    // days and max count
	    id = "";
	    subject = "";
	    // ID, subject, trigger days and maximum count are required
	    id = n.getAttributes().getNamedItem("ID").getNodeValue();
	    subject = n.getAttributes().getNamedItem("Subject").getNodeValue();

	    // email format is optional
	    Node node = n.getAttributes().getNamedItem("Format");
	    if (node != null)
		html_format = node.getNodeValue().equalsIgnoreCase("html");
	    // System.out.println("ID - "+id);
	    // read out the contents of the email message
	    NodeList node_p = n.getChildNodes();
	    boolean has_ref = false;
	    main_body = "";
	    html_header = "";
	    html_body = "";
	    html_tail = "";
	    html_signature = "";

	    for (int j = 0; j < node_p.getLength(); j++) {
		if (node_p.item(j).getNodeName()
			.equalsIgnoreCase("Message_Ref")
			&& !has_ref) // check prevents a 2nd ref (tho that's
		// invalid)
		{
		    msg_ref = node_p.item(j).getAttributes().getNamedItem("ID")
			    .getNodeValue();
		    has_ref = true;
		    break;
		} else {
		    if (node_p.item(j).getNodeName().equalsIgnoreCase("p")) {
			main_body += node_p.item(j).getFirstChild()
				.getNodeValue()
				+ "\n\n";
			html_body += "<p>"
				+ node_p.item(j).getFirstChild().getNodeValue()
				+ "</p>\n";
		    }
		    if (node_p.item(j).getNodeName().equalsIgnoreCase("s")) {
			if (html_format)
			    html_signature += "<p>"
				    + node_p.item(j).getFirstChild()
					    .getNodeValue() + "</p>\n";
			else {
			    main_body += node_p.item(j).getFirstChild()
				    .getNodeValue()
				    + "\n\n";
			    html_body += "<p>"
				    + node_p.item(j).getFirstChild()
					    .getNodeValue() + "</p>\n";
			}
		    }
		    // mark the URL link
		    if (node_p.item(j).getNodeName().equalsIgnoreCase("link")) {
			has_link = true;
			main_body = main_body + "URL LINK\n\n";
			html_body += "<p align=center><font color='blue'>[<u>URL Link to Start the Survey</u>]</font></p>\n";
		    }
		    // mark the decline URL link
		    if (node_p.item(j).getNodeName()
			    .equalsIgnoreCase("declineLink")) {
			has_dlink = true;
			main_body = main_body + "DECLINE LINK\n\n";
			html_body += "<p align=center><font color='blue'>[<u>URL Link to Decline the Survey</u>]</font></p>\n";
		    }

		}
	    }
	} catch (Exception e) {
	    AdminInfo.log_error("WISE - TYPE MESSAGE: ID = " + id
		    + "; Subject = " + subject + " --> " + e.toString(), e);
	    return;
	}
    }

    // Resolve message references. Do this after construct-time so that order of
    // messages in file won't matter
    public void resolveRef(Preface myPreface) {
	try {
	    if (msg_ref != null) {
		Message refd_msg = myPreface.get_message(msg_ref);
		if (refd_msg.msg_ref == null) {
		    main_body = refd_msg.main_body;
		    has_link = refd_msg.has_link;
		    has_dlink = refd_msg.has_dlink;
		    html_tail = refd_msg.html_tail;
		    html_header = refd_msg.html_header;
		} else
		    WISE_Application
			    .log_error(
				    "MESSAGE: ID = "
					    + id
					    + "; Subject = "
					    + subject
					    + " refernces a message that itself has a message ref. Double-indirection not supported. ",
				    null);
	    }
	} catch (Exception e) {
	    WISE_Application.log_error("Failed to resolve ref MESSAGE: ID = "
		    + id + "; Subject = " + subject + " --> " + e.toString(),
		    null);
	    return;
	}
    }

    public void setHrefs(String servletPath, String imgRootPath) {
	servlet_path = servletPath;
	if (html_format) {
	    // compose the html header and tail
	    html_header = "<table width=510 border=0 cellpadding=0 cellspacing=0>"
		    + "<tr><td rowspan=5 width=1 bgcolor='#996600'></td>"
		    + "<td width=500 height=1 bgcolor='#996600'></td>"
		    + "<td rowspan=5 width=1 bgcolor='#996600'></td></tr>"
		    + "<tr><td height=120 align=center><img src='"
		    + WISE_Application.rootURL
		    + "/"
		    + WISE_SHARED
		    + "/image?img="
		    + headerImgFilename
		    + "'></td></tr>"
		    + "<tr><td>"
		    + "<table width=100% border=0 cellpadding=0 cellspacing=0>"
		    + "<tr><td width=20>&nbsp;</td>"
		    + "<td width=460><font size=1 face='Verdana'>\n\n";

	    // NOTE: signature included in the tail
	    html_tail = "</font></td><td width=20>&nbsp;</td>"
		    + "</tr></table></td></tr>" + "<tr><td>"
		    + "<table width=100% border=0 cellpadding=0 cellspacing=0>"
		    + "<tr><td rowspan=2 width=25>&nbsp;</td>"
		    + "<td height=80 width=370><font size=1 face='Verdana'>"
		    + html_signature
		    + "</font></td>"
		    + "<td rowspan=2 height=110 width=105 align=left valign=bottom><img src=\""
		    + WISE_Application.rootURL
		    + "/"
		    + WISE_SHARED
		    + "/image?img="
		    + footerImgFilename2
		    + "\"></td></tr>"
		    + "<tr><td height=30 width=370 align=center valign=bottom><img src='"
		    + WISE_Application.rootURL
		    + "/"
		    + WISE_SHARED
		    + "/image?img="
		    + footerImgFilename1
		    + "'></td></tr>"
		    + "</table></td></tr>"
		    + "<tr><td width=500 height=1 bgcolor='#996600'></td></tr></table>\n\n";
	}
    }

    public String compose_text_body(String salutation, String lastname,
	    String ssid, String msg_index) {
	String text_body = null;
	// compose the text body
	text_body = "Dear " + salutation + " " + lastname + ":\n\n" + main_body;

	if (has_link) {
	    // Manoj changes
	    // String reminder_link =
	    // servlet_path+"begin?msg="+WISE_Application.encode(msg_index)
	    // +"&t="+WISE_Application.encode(ssid);
	    String reminder_link = servlet_path + "survey?msg="
 + msg_index
		    + "&t="
		    + WISE_Application.encode(ssid);

	    String decline_link = servlet_path + "survey/declineNOW?m="
		    + msg_index + "&t="
		    + WISE_Application.encode(ssid);

	    text_body = text_body.replaceAll("URL LINK", reminder_link + "\n");
	    if (has_dlink)
		text_body = text_body.replaceAll("DECLINE LINK", decline_link
			+ "\n");
	}
	return text_body;
    }

    public static String buildInviteUrl(String servletPath, String msgIndex,
	    String studySpaceId, String surveyId) {
	// t = xxxx -> study space
	// m = yyyy -> survey_user_message_space
	// s = zzzz -> survey id
	// for anonymous user we do not have survey message user and it is not
	// possible to get survey from study space while knowing the survey for
	// annonymous users because study space can have multiple surveys.
	if (msgIndex == null)
	    return servletPath + "survey?t="
		    + WISE_Application.encode(studySpaceId) + "&s="
		    + CommonUtils.base64Encode(surveyId);
	return servletPath + "survey?msg=" + msgIndex
		+ "&t=" + WISE_Application.encode(surveyId);
    }

    public String compose_html_body(String salutation, String lastname,
	    String ssid, String msg_index) {
	if (!html_format)
	    return null; // null return is the external signal the message
	// doesn't have an HTML version

	// this overrides the iVar TODO: FIX so that we can actually use it here
	// and for overview display
	String html_body = null;
	// add the html header & the top of the body to the html body
	html_body = htmlOpen + html_header;
	html_body += "<p><b>Dear " + salutation + " " + lastname + ":</b></p>"
		+ main_body;
	html_body = html_body.replaceAll("\n", "<br>");
	if (has_link) {
	    // String reminder_link =
	    // servlet_path+"begin?msg="+WISE_Application.encode(msg_index)
	    String reminder_link = servlet_path + "survey?msg="
 + msg_index
		    + "&t="
		    + WISE_Application.encode(ssid);
	    String decline_link = servlet_path + "declineNOW?m="
 + msg_index
		    + "&t="
		    + WISE_Application.encode(ssid);
	    html_body = html_body.replaceAll("URL LINK",
		    "<p align=center><a href='" + reminder_link + "'>"
			    + reminder_link + "</a></p>");
	    if (has_dlink)
		html_body = html_body.replaceAll("DECLINE LINK",
			"<p align=center><a href='" + decline_link + "'>"
				+ decline_link + "</a></p>");
	}
	// append the bottom part of body for the html email
	html_body += html_tail + htmlClose;
	return html_body;
    }

    // render html table rows to complete sample display page (used by Admin)
    public String renderSample_asHtmlRows() {
	String outputString = "<tr><td class=sfon>Subject: </td>";
	outputString += "<td>" + subject + "</td></tr>";
	outputString += "<tr><td colspan=2>";

	// add the the bottom imag & signature to the html body
	if (html_format) {
	    outputString += html_header;
	    outputString += "<p>Dear [Salutation] [Name]:</p>";
	    outputString += html_body;
	    outputString += html_tail; // note: includes signature
	} else {
	    outputString += "<table width=100% border=0 cellpadding=0 cellspacing=0>";
	    outputString += "<tr><td>&nbsp;</td></tr>";
	    outputString += "<tr><td colspan=2><font size=1 face='Verdana'><p>Dear [Salutation] [Name],</p>\n";
	    outputString += main_body
		    + "<p>&nbsp;</p></font></td></tr></table>\n\n";
	}
	outputString += "</td></tr>";
	return outputString;
    }

    @Override
    public String toString() {
	return "<P><B>Message</b> ID: " + id + "<br>\n" + "References: "
		+ msg_ref + "<br>\n" + "Subject: " + subject + "<br>\n"
		+ "Body: " + main_body + "</p>\n";
    }
    /*
     * DEPRECATED public String irb() { return message_sequence.irb_id; } public
     * String survey() { return message_sequence.survey_id; }
     */
}
