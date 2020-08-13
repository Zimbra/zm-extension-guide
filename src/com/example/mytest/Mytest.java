/*

Copyright (C) 2016-2020  Barry de Graaff

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see http://www.gnu.org/licenses/.

*/

package com.example.mytest;

import com.zimbra.cs.extension.ExtensionHttpHandler;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.AuthToken;
import com.zimbra.cs.account.Cos;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;

public class Mytest extends ExtensionHttpHandler {

    /**
     * The path under which the handler is registered for an extension.
     * return "/mytest" makes it show up under:
     * https://testserver.example.com/service/extension/mytest
     * @return path
     */
    @Override
    public String getPath() {
        return "/mytest";
    }
    /**
     * Processes HTTP GET requests.
     *
     * @param req  request message
     * @param resp response message
     * @throws java.io.IOException
     * @throws javax.servlet.ServletException
     */
    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        //Fetch a static HTML from the JAR file.
        InputStream in = getClass().getResourceAsStream("/page.html");
        //Set the content type and charset for the response to the client.
        resp.setHeader("Content-Type", "text/html;charset=UTF-8");
        //Copy the contents of the static HTML to the response.
        in.transferTo(resp.getOutputStream());
    }

    /**
     * Processes HTTP POST requests.
     *
     * @param req  request message
     * @param resp response message
     * @throws java.io.IOException
     * @throws javax.servlet.ServletException
     */
    /* https://stackoverflow.com/questions/2422468/how-to-upload-files-to-server-using-jsp-servlet
    nano /opt/zimbra/jetty_base/etc/service.web.xml.in
    nano /opt/zimbra/jetty_base/webapps/service/WEB-INF/web.xml
    Add multipart config to enable HttpServletRequest.getPart() and HttpServletRequest.getParts()
        <servlet>
          <servlet-name>ExtensionDispatcherServlet</servlet-name>
          <servlet-class>com.zimbra.cs.extension.ExtensionDispatcherServlet</servlet-class>
          <async-supported>true</async-supported>
          <load-on-startup>2</load-on-startup>
          <init-param>
            <param-name>allowed.ports</param-name>
            <param-value>8080, 8443, 7071, 7070, 7072, 7443</param-value>
          </init-param>
        <multipart-config>
        </multipart-config>
        </servlet>
    And restart Zimbra
    */
    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        //Read cookies and look for the ZM_AUTH_TOKEN cookie.
        String authTokenStr = null;

        Cookie[] cookies = req.getCookies();
        for (int n = 0; n < cookies.length; n++) {
            Cookie cookie = cookies[n];

            if (cookie.getName().equals("ZM_AUTH_TOKEN")) {
                authTokenStr = cookie.getValue();
                break;
            }
        }

        //Validate active user by parsing Zimbra AuthToken and read a cos value to see if its a valid user.
        Account account = null;

        if (authTokenStr != null) {
            try {
                AuthToken authToken = AuthToken.getAuthToken(authTokenStr);
                Provisioning prov = Provisioning.getInstance();
                account = Provisioning.getInstance().getAccountById(authToken.getAccountId());
                Cos cos = prov.getCOS(account);
                Set<String> allowedDomains = cos.getMultiAttrSet(Provisioning.A_zimbraProxyAllowedDomains);
                //In case no exception was thrown, it is a valid user.
            } catch (Exception ex) {
                //This was not a valid authtoken.
                ex.printStackTrace();
                return;
            }
        } else {
            //There was no authtoken found.
            return;
        }

        try {
            //Get the jsondata field from the multipart request send to the server and parse it to JSON Object.
            JSONObject receivedJSON = new JSONObject(IOUtils.toString(req.getPart("jsondata").getInputStream(), "UTF-8"));

            //Initialize some variables to hold the binary files posted to the server.
            JSONObject filesParent = new JSONObject();
            JSONObject files = new JSONObject();

            //Retrieves <input type="file" name="filesToUpload[]" multiple="true">
            List<Part> fileParts = req.getParts().stream().filter(part -> "filesToUpload[]".equals(part.getName()) &&
                    part.getSize() > 0).collect(Collectors.toList());

            //Iterate through the received files and put them in the JSON object files.
            for (Part filePart : fileParts) {
                String fileName = Paths.get(filePart.getSubmittedFileName()).getFileName().toString(); // MSIE fix.
                String fileContent = Base64.getEncoder().encodeToString(IOUtils.toByteArray(filePart.getInputStream()));
                fileContent = "data:" + filePart.getContentType() + ";base64," + fileContent;
                files.put(fileName, fileContent);
            }

            //Put the files JSON Object in a parent
            filesParent.put("files", files);
            //Combine the JSON files with parent with the JSON sent from the client.
            //This demonstrates we parsed it correctly and did not fool up any encoding.
            JSONObject responseJSON = mergeJSONObjects(receivedJSON, filesParent);

            //Set the content type for the response.
            resp.setHeader("Content-Type", "text/json;charset=UTF-8");
            //Do the response to the client.
            resp.getOutputStream().print(responseJSON.toString());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //Helper method to combine JSON objects
    public static JSONObject mergeJSONObjects(JSONObject json1, JSONObject json2) {
        JSONObject mergedJSON = new JSONObject();
        try {
            mergedJSON = new JSONObject(json1, JSONObject.getNames(json1));
            for (String crunchifyKey : JSONObject.getNames(json2)) {
                mergedJSON.put(crunchifyKey, json2.get(crunchifyKey));
            }

        } catch (JSONException e) {
            throw new RuntimeException("JSON Exception" + e);
        }
        return mergedJSON;
    }
}
