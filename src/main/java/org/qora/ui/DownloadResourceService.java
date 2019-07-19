package org.qora.ui;

import java.io.IOException;
import java.util.Enumeration;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpContent;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.ResourceService;
import org.eclipse.jetty.util.URIUtil;

/**
 * Replace ResourceService that delivers content as "attachments", typically forcing download instead of rendering.
 * <p>
 * Sets <tt>Content-Type</tt> header to <tt>application/octet-stream</tt><br>
 * Sets <tt>Content-Disposition</tt> header to <tt>attachment; filename="<i>basename</i>"</tt><br>
 * where <i>basename</i> is that last component of requested URI path.
 * <p>
 * Example usage:<br>
 * <br>
 * <tt>... = new ServletHolder("servlet-name", new DefaultServlet(new DownloadResourceService()));</tt>
 */
public class DownloadResourceService extends ResourceService {

	@Override
	protected boolean sendData(HttpServletRequest request, HttpServletResponse response, boolean include, final HttpContent content, Enumeration<String> reqRanges) throws IOException {
		final boolean _pathInfoOnly = super.isPathInfoOnly();
		String servletPath = _pathInfoOnly ? "/" : request.getServletPath();
		String pathInfo = request.getPathInfo();
		String pathInContext = URIUtil.addPaths(servletPath,pathInfo);

		// Find basename of requested content
		final int slashIndex = pathInContext.lastIndexOf(URIUtil.SLASH);
		if (slashIndex != -1)
			pathInContext = pathInContext.substring(slashIndex + 1);

		// Add appropriate headers
		response.setHeader(HttpHeader.CONTENT_TYPE.asString(), "application/octet-stream");
		response.setHeader("Content-Disposition", "attachment; filename=\"" + pathInContext + "\"");

		return super.sendData(request, response, include, content, reqRanges);
	}

}
