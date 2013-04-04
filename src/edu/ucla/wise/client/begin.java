package edu.ucla.wise.client;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import edu.ucla.wise.commons.CommonUtils;
import edu.ucla.wise.commons.Study_Space;
import edu.ucla.wise.commons.Surveyor_Application;
import edu.ucla.wise.commons.User;
import edu.ucla.wise.commons.WISE_Application;
import edu.ucla.wise.commons.WiseConstants;

/*
 Direct the user coming from email URL or interviewers to appropriate next step or page
 */

public class begin extends HttpServlet {
    static final long serialVersionUID = 1000;

    @Override
    public void service(HttpServletRequest req, HttpServletResponse res)
	    throws IOException
 {
	res.setContentType("text/html");
	PrintWriter out = res.getWriter();

	// Check if user hit counter indicates heavy traffic
	if (UserHitCounter.getInstance().getNumberOfUserAccesses() > 100000) {
	    out.println("Too many users in the system"
		    + "<p> WISE Begin failed </p>"
		    + edu.ucla.wise.commons.Surveyor_Application.initErrorHtmlFoot);
	    return;
	} else {
	    UserHitCounter.getInstance().incrementNumberOfUserAccesses();
	}

	// Initialize surveyor application if not already started
	String initErr = Surveyor_Application.check_init(req.getContextPath());

	if (initErr != null) {
	    out.println(initErr
		    + "<p> WISE Begin failed </p>"
		    + edu.ucla.wise.commons.Surveyor_Application.initErrorHtmlFoot);
	    WISE_Application.log_error("WISE Surveyor Init Error: " + initErr,
		    null);// should write to file if no email
	    return;
	}

	// prepare for writing
	HttpSession session = req.getSession(true);

	// get the ecoded study space ID
	String spaceid_encode = req.getParameter("t");

	// get the email message ID
	String msgid = req.getParameter("msg");

	// get encoded survey ID
	String surveyid_encode = req.getParameter("s");

	// TODO: check if the user is actually a interviewer (i=interview)
	// String interview_begin = req.getParameter("i");
	// if (interview_begin != null) {
	// session.setAttribute("INTERVIEW", interview_begin);
	// }

	// if it is not an interview, but can't get sufficient information,
	// then the email URL maybe broken into lines
	if (CommonUtils.isEmpty(spaceid_encode)) {
	    res.sendRedirect(Surveyor_Application.shared_file_url
		    + "incorrectUrl"
		    + edu.ucla.wise.commons.Surveyor_Application.html_ext);
	    return;
	}
	// This is a general email address without any specific invitee
	// information
	// Hence, ask the user to enter his details so that invitee shall be
	// created.
	if (CommonUtils.isEmpty(msgid)) {
	    StringBuffer destination = new StringBuffer();
	    destination.append("/WISE/survey/").append(
		    WiseConstants.NEW_INVITEE_JSP_PAGE);
	    if (surveyid_encode == null) {
		res.sendRedirect(Surveyor_Application.shared_file_url
			+ "link_error"
			+ edu.ucla.wise.commons.Surveyor_Application.html_ext);
		return;
	    }
	    res.sendRedirect(destination.toString() + "?s=" + surveyid_encode
		    + "&t=" + spaceid_encode);
	    return;
	}

	String spaceid;
	User theUser;

	// decode study space ID
	spaceid = WISE_Application.decode(spaceid_encode);

	// initiate the study space ID and put it into the session
	session.removeAttribute("STUDYSPACE");
	Study_Space theStudy = Study_Space.get_Space(spaceid);
	session.setAttribute("STUDYSPACE", theStudy);

	if (theStudy == null) {
	    res.sendRedirect(Surveyor_Application.shared_file_url
		    + "link_error"
		    + edu.ucla.wise.commons.Surveyor_Application.html_ext);
	    return;
	}

	// get the user ID
	theUser = (User) session.getAttribute("USER");

	// create a new User if none is already found in the session
	if (theUser == null)
	    theUser = theStudy.get_User(msgid);
	// might double-check user's validity otherwise but need to write
	// new fn
	// 'cause all we have is msgid not userid

	// if the user can't be retrieved or created, send error info
	if (theUser == null || theUser.id == null) {
	    out.println("<HTML><HEAD><TITLE>WISE survey system -- Can't identify you</TITLE>"
		    + "<LINK href='styleRender?app="
		    + theStudy.study_name
		    + "&css=style.css' type=text/css rel=stylesheet>"
		    + "<body text=#000000><center><table>"
		    + "<tr><td>Sorry, the information in your email invitation didn't identify you."
		    + "Please check with person who sent your invitation.</td></tr>"
		    + "</table></center></body></html>");
	    WISE_Application.log_error(
		    "WISE Error: Begin servlet failed for message id " + msgid,
		    null);
	    return;
	}

	// put the user into the session
	session.setAttribute("USER", theUser);

	String main_url;
	if ((Surveyor_Application.shared_file_url != null)
		|| (Surveyor_Application.shared_file_url.length() != 0)) {
	    main_url = "" + Surveyor_Application.shared_file_url
		    + "browser_check"
		    + edu.ucla.wise.commons.Surveyor_Application.html_ext
		    + "?w=" + Surveyor_Application.servlet_url + "start"; // pass
	    // along
	    // fully-resolved
	    // address
	    // of
	    // triage
	    // servlet
	} else {
	    System.err.println("servlet URL is "
		    + Surveyor_Application.servlet_url);
	    main_url = "file_test/" + "browser_check"
		    + edu.ucla.wise.commons.Surveyor_Application.html_ext
		    + "?w=" + Surveyor_Application.servlet_url + "start"; // pass
	    // along
	    // fully-resolved
	    // address
	    // of
	    // triage
	    // servlet
	    WISE_Application.log_error("Main URL is [" + main_url + "]", null);
	}
	// Debugging statements
	// if(Surveyor_Application.shared_file_url == null)
	// out.println("Surveyor_Application.shared_file_url is NULL!!");
	// else
	// out.println("Surveyor's shared URL is [" +
	// Surveyor_Application.shared_file_url + "]");
	// out.println("Surveyor's main URL is [" + main_url + "]");
	// out.println("Loading survey...");
	// create a top frame to catch up the CLOSE WINDOW event
	// check the survey requests on the bottom frame
	out.println("<HTML><HEAD><SCRIPT LANGUAGE=\"JavaScript1.1\">");
	out.println("<!--");
	out.println("top.location.replace('" + main_url + "');");
	out.println("// -->");
	out.println("</SCRIPT>");
	out.println("</HEAD>");
	out.println("<frameset rows='1,*' frameborder='NO' border=0 framespacing=0>");
	out.println("<frame name='topFrame' scrolling='NO' noresize src=''>");
	out.println("<frame name='mainFrame' src='"
		+ Surveyor_Application.shared_file_url
		+ "error_javascript.htm'>");
	out.println("</frameset><noframes></noframes></HTML>");
	out.close();

	    }

}
