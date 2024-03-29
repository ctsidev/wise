package edu.ucla.wise.admin;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.log4j.Logger;

import edu.ucla.wise.commons.AdminInfo;
import edu.ucla.wise.commons.WiseConstants;

/*import org.w3c.dom.*;
 import java.net.*;
 import sun.net.smtp.SmtpClient;
 import java.text.*;
 import java.lang.*;
 import java.util.*;
 */

public class print_survey extends HttpServlet {

    private static final long serialVersionUID = 1L;

    Logger log = Logger.getLogger(print_survey.class);

    @Override
    public void service(HttpServletRequest req, HttpServletResponse res)
 {
	log.info("Admin print survey called");
	try {
	    // prepare for writing
	    PrintWriter out;
	    res.setContentType("text/html");
	    out = res.getWriter();
	    HttpSession session = req.getSession(true);
	    // check if the session is still valid
	    String survey_id = req.getParameter("s");
	    AdminInfo admin_info = (AdminInfo) session
		    .getAttribute("ADMIN_INFO");
	    if (admin_info == null) // if session does not
	    // exists
	    {
		out.println("Wise Admin - Print Survey Error: Can't get the Admin Info");
		return;
	    }
	    if (survey_id == null) {
		out.println("Wise Admin - Print Survey Error: Can't get the survey id");
		return;
	    }


	    // Changing the URL pattern
	    String new_url = admin_info.getStudyServerPath() + "/"
		    + WiseConstants.ADMIN_APP + "/" + "admin_print_survey?SID="
		    + admin_info.study_id + "&a=FIRSTPAGE&s=" + survey_id;
	    res.sendRedirect(new_url);

	    out.close();
	} catch (IOException e) {
	    log.error("IO Exception  while printing survey", e);
	}
    }

}
