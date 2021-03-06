/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information, please refer to <http://unlicense.org/>
 */

package net.adamcin.httpsig.http.servlet;

import net.adamcin.httpsig.api.Authorization;
import net.adamcin.httpsig.api.Challenge;
import net.adamcin.httpsig.api.Constants;
import net.adamcin.httpsig.api.RequestContent;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

/**
 * Helper methods for use in a Servlet context.
 */
public final class ServletUtil {

    private ServletUtil() {
    }

    /**
     * Parse an {@link javax.servlet.http.HttpServletRequest} to create an {@link net.adamcin.httpsig.api.Authorization}
     * @param request the HTTP Request
     * @return the {@link Authorization}
     */
    public static Authorization getAuthorization(HttpServletRequest request) {
        Enumeration headerValues = request.getHeaders(Constants.AUTHORIZATION);
        while (headerValues.hasMoreElements()) {
            String headerValue = (String) headerValues.nextElement();
            Authorization authorization = Authorization.parse(headerValue);
            if (authorization != null) {
                return authorization;
            }
        }

        return null;
    }

    /**
     * Parse an {@link javax.servlet.http.HttpServletRequest} to build the {@link net.adamcin.httpsig.api.RequestContent}
     * @param request the HTTP Request
     * @return the {@link net.adamcin.httpsig.api.RequestContent}
     */
    public static RequestContent getRequestContent(HttpServletRequest request) {
        return getRequestContent(request, null);
    }

    /**
     * Parse an {@link javax.servlet.http.HttpServletRequest} to build the {@link net.adamcin.httpsig.api.RequestContent}
     * @param request the HTTP Request
     * @param ignoreHeaders a collection of header names to ignore, in case they have been added by proxies
     * @return
     */
    public static RequestContent getRequestContent(HttpServletRequest request, Collection<String> ignoreHeaders) {
        final Set<String> _ignore = new HashSet<String>();

        if (ignoreHeaders != null) {
            for (String ignore : ignoreHeaders) {
                _ignore.add(ignore.toLowerCase());
            }
        }

        RequestContent.Builder signatureContent = new RequestContent.Builder();
        String path = request.getRequestURI() + (request.getQueryString() != null ? "?" + request.getQueryString() : "");

        signatureContent.setRequestTarget(request.getMethod(), path);
        signatureContent.setRequestLine(request.getMethod() + " " + path + " " + request.getProtocol());

        Enumeration headerNames = request.getHeaderNames();

        while (headerNames.hasMoreElements()) {
            String headerName = (String) headerNames.nextElement();
            if (!_ignore.contains(headerName.toLowerCase())) {
                Enumeration headerValues = request.getHeaders(headerName);
                while (headerValues.hasMoreElements()) {
                    String headerValue = (String) headerValues.nextElement();
                    signatureContent.addHeader(headerName, headerValue);
                }
            }
        }

        return signatureContent.build();
    }

    /**
     * Handle an {@link javax.servlet.http.HttpServletResponse} which has failed authentication by sending a
     * {@link net.adamcin.httpsig.api.Challenge} header
     * @param resp the HTTP Response
     * @param challenge the Server challenge parameters
     * @return true if response was flushed successfully, false otherwise
     * @throws IOException if anything went wrong
     */
    public static boolean sendChallenge(HttpServletResponse resp, Challenge challenge) throws IOException {
        if (!resp.isCommitted()) {
            resp.resetBuffer();
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setHeader(Constants.CHALLENGE, challenge.getHeaderValue());
            resp.flushBuffer();
            return true;
        }
        return false;
    }
}
