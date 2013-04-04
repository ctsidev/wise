package edu.ucla.wise.client;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Hashtable;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import edu.ucla.wise.commons.Interviewer;
import edu.ucla.wise.commons.Surveyor_Application;
import edu.ucla.wise.commons.User;
import edu.ucla.wise.commons.WISE_Application;

/*
 Read the survey results from the form on the survey page after it's submitted by the user
 */

public class readform extends HttpServlet {
    static final long serialVersionUID = 1000;

    public String pageReplace_html(String new_page) {
	return "<html>" + "<head><script LANGUAGE='javascript'>"
		+ "top.location.replace('" + new_page + "');"
		+ "</script></head>" + "<body></body>" + "</html>";
    }

    @Override
    public void service(HttpServletRequest req, HttpServletResponse res)
	    throws ServletException, IOException {
	// prepare to write
	PrintWriter out;
	res.setContentType("text/html");
	out = res.getWriter();

	HttpSession session = req.getSession(true);
	Surveyor_Application s = (Surveyor_Application) session
		.getAttribute("SurveyorInst");
	if (session.isNew()) {
	    res.sendRedirect(s.shared_file_url + "/error"
		    + Surveyor_Application.html_ext);
	    return;
	}

	// get the user from session
	User theUser = (User) session.getAttribute("USER");
	if (theUser == null) {
	    out.println("<p>Error: Can't find the user info.</p>");
	    return;
	}

	// get all the fields values from the form and save them in the hash
	// table
	Hashtable params = new Hashtable();
	String n, v;
	Enumeration e = req.getParameterNames();
	while (e.hasMoreElements()) {
	    n = (String) e.nextElement();
	    v = req.getParameter(n);
	    params.put(n, v);
	}

	String action = req.getParameter("action");
	if ((action == null) || (action.equals(""))) // if no action value is
						     // specified, fill in
						     // default
	    action = "NEXT";

	String new_page = "";

	if (action.equalsIgnoreCase("linkpage")) // User jumping to page
						 // selected from progress
						 // bar
	{
	    // the next page will be the page clicked by the user or the
	    // interviewer
	    theUser.readAndAdvancePage(params, false);
	    String link_page_id = req.getParameter("nextPage");
	    theUser.set_page(link_page_id);
	    new_page = "view_form?p=" + theUser.currentPage.id;
	    out.println("<html>");
	    out.println("<head></head>");
	    out.println("<body ONLOAD=\"self.location = '" + new_page
		    + "';\"></body>");
	    out.println("</html>");
	}
	// Detect interrupt states; forward to appropos page
	else if (action.equalsIgnoreCase("INTERRUPT")) {
	    theUser.readAndAdvancePage(params, false);
	    theUser.set_interrupt();
	    session.invalidate();
	    new_page = s.shared_file_url + "interrupt"
		    + Surveyor_Application.html_ext;
	    out.println(pageReplace_html(new_page));
	    return;
	}
	// if it is an timeout event, then show the timeout info
	else if (action.equalsIgnoreCase("TIMEOUT")) {
	    theUser.readAndAdvancePage(params, false);
	    theUser.set_interrupt();
	    session.invalidate();
	    new_page = s.shared_file_url + "timeout"
		    + Surveyor_Application.html_ext;
	    out.println(pageReplace_html(new_page));
	    return;
	}
	// if it is an abort event (entire window was closed), then record
	// event; nothing to show
	else if (action.equalsIgnoreCase("ABORT")) {
	    theUser.readAndAdvancePage(params, false);
	    theUser.set_interrupt();
	    session.invalidate(); // should force user object to be dropped &
				  // connections to be cleaned up
	    return;
	} else // either done or continuing; go ahead and advance page
	{
	    // give user submitted http params to record & process
	    theUser.readAndAdvancePage(params, true);
	}

	if (theUser.completed_survey()) {
	    // check if it is an interview process
	    Interviewer inv = (Interviewer) session.getAttribute("INTERVIEWER");
	    if (inv != null) {
		// record interview info in the database
		inv.set_done();
		// remove the current user info
		session.removeAttribute("USER");
		// redirect to the show overview page
		new_page = s.shared_file_url + "interview/Show_Assignment.jsp";
	    } else {
		// redirect the user to the forwarding URL specified in survey
		// xml file
		if (theUser.currentSurvey.forward_url != null
			&& !theUser.currentSurvey.forward_url
				.equalsIgnoreCase("")) {
		    // for example:
		    // forward_url="http://localhost:8080/ca/servlet/begin?t="
		    new_page = theUser.currentSurvey.forward_url;
		    // if the EDU ID (sage space ID) is specified in survey xml,
		    // then add it to the URL
		    if (theUser.currentSurvey.edu_module != null
			    && !theUser.currentSurvey.edu_module
				    .equalsIgnoreCase(""))
			// new_page = new_page +
			// "/"+theUser.currentSurvey.study_space.dir_name+"/servlet/begin?t="
			new_page = new_page
				+ "/"
				+ theUser.currentSurvey.study_space.dir_name
				+ "/survey?t="
				+ WISE_Application
					.encode(theUser.currentSurvey.edu_module)
				+ "&r=" + WISE_Application.encode(theUser.id);
		    // otherwise the link will be the URL plus the user ID
		    else {
			new_page = new_page
				+ "?s="
				+ WISE_Application.encode(theUser.id)
				+ "&si="
				+ theUser.currentSurvey.id
				+ "&ss="
				+ WISE_Application
					.encode(theUser.currentSurvey.study_space.id);
			WISE_Application.log_info(new_page
				+ readform.class.getName());
		    }
		}
		// TODO: (med) fix thank-you page to be a servlet or JSP
		// go to the thanks html page without results review
		else {
		    // Setting the User state to completed.
		    theUser.set_complete();

		    // -1 is default if no results are going to be reviewed.
		    if (theUser.currentSurvey.min_completers == -1) {
			new_page = s.shared_file_url + "thank_you";

		    }
		    // go to results review
		    else {
			// send the view result email only once when it reaches
			// the min number of completers
			int current_numb_completers = theUser
				.check_completion_number();
			String review = "false";

			if (current_numb_completers >= theUser.currentSurvey.min_completers) {

			    review = "view_results";
			}

			// redirect to the thank you html with the review link
			// for the current user and future completers
			new_page = s.shared_file_url + "/thank_you?review="
				+ review;
		    }
		}

	    } // end of else (not interview)
	    out.println(pageReplace_html(new_page));
	} // end of if(survey_completed())

	else // continue to the next page
	{
	    // form the link to the next page
	    new_page = "view_form?p=" + theUser.currentPage.id;

	    out.println("<html>");
	    out.println("<head></head>");
	    out.println("<body ONLOAD=\"self.location = '" + new_page
		    + "';\"></body>");
	    out.println("</html>");
	}

	out.close();
    }
}
