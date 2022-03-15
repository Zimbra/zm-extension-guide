package com.example.mytest;

import com.zimbra.cs.extension.ExtensionHttpHandler;
import com.zimbra.cs.account.AuthToken;
import com.zimbra.cs.servlet.util.AuthUtil;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
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
     *
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
        //all authentication is done by AuthUtil.getAuthTokenFromHttpReq, returns null if unauthorized
        final AuthToken authToken = AuthUtil.getAuthTokenFromHttpReq(req, resp, false, true);
        if (authToken != null) {
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
