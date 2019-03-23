import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.net.HttpCookie;
import java.net.URLDecoder;
import java.sql.*;
import java.util.*;

import java.sql.Connection;
import java.sql.DriverManager;

public class Login implements HttpHandler {
    private static final String SESSION_COOKIE_NAME = "sessionId";
    CookieHelper cookieHelper = new CookieHelper();

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String method = httpExchange.getRequestMethod();

        if (method.equals("GET")) {
            System.out.println("GET");
            Optional<HttpCookie> sessionIdCookie = getSessionIdCookie(httpExchange);
            if (sessionIdCookie.isPresent()) {
                Optional<HttpCookie> userNameCookie = getUserNameCookie(httpExchange);
                if (userNameCookie.isPresent()) {

                    String userName = getUserNameFromCookie(httpExchange);
                    String sessionId = getSessionIdFromCookie(httpExchange);
                    if (userHasActiveSessionInDatabase(userName, sessionId)) {
                        System.out.println("User already has active session in database");
                        System.out.println("location change to logged page");
                        httpExchange.getResponseHeaders().set("Location", "/logged");
                        httpExchange.sendResponseHeaders(302, 0);
                    }
                }
            }
                String response = "<html><body>" +
                        "<form method=\"POST\">\n" +
                        "  User Name:<br>\n" +
                        "  <input type=\"text\" name=\"userName\" value=\"\">\n" +
                        "  <br>\n" +
                        "  Password:<br>\n" +
                        "  <input type=\"text\" name=\"password\" value=\"\">\n" +
                        "  <br><br>\n" +
                        "  <input type=\"submit\" value=\"LOG IN\">\n" +
                        "</form> " +
                        "</body></html>";
                sendResponse(httpExchange, response);
            }


        if (method.equals("POST")) {
            System.out.println("POST Login.java");
            InputStreamReader isr = new InputStreamReader(httpExchange.getRequestBody(), "utf-8");
            BufferedReader br = new BufferedReader(isr);
            String formData = br.readLine();
            System.out.println(formData);
            Map inputs = parseFormData(formData);

            Optional<Boolean> loginMatchesPasswordInDB = findUserInDatabase(inputs.get("userName").toString(), inputs.get("password").toString());
            if (loginMatchesPasswordInDB.get()) {
                System.out.println("login and password found in database");
                    HttpCookie cookieUserName = new HttpCookie("username", inputs.get("userName").toString());
                    httpExchange.getResponseHeaders().add("Set-Cookie", cookieUserName.toString());
                    String sessionId = UUID.randomUUID().toString();
                    HttpCookie cookieSessionId = new HttpCookie(SESSION_COOKIE_NAME, sessionId);
                    httpExchange.getResponseHeaders().add("Set-Cookie", cookieSessionId.toString());
                    addSessionToDB(inputs.get("userName").toString() ,sessionId);

                    System.out.println("session added to database, redirecting to logged page");
                    httpExchange.getResponseHeaders().set("Location", "/logged");
                    httpExchange.sendResponseHeaders(302, 0);
                } else {
                System.out.println("wrong username or password, login failed, lacation change to login");
                httpExchange.getResponseHeaders().set("Location", "/login");
                httpExchange.sendResponseHeaders(302, 0);
            }
        }
    }

    private void addSessionToDB(String userName, String sessionId) {
        Connection c;
        PreparedStatement sqlStatement;
        try {
            c = DriverManager.getConnection("jdbc:postgresql://localhost:5432/si4logindb", "konrad", "1234");
            sqlStatement = c.prepareStatement("INSERT INTO sessioninfo (username, sessionid) VALUES (?, ?);");
            sqlStatement.setString(1, userName);
            sqlStatement.setString(2, sessionId);
            sqlStatement.executeUpdate();
            c.close();
        } catch (SQLException e) {
            System.err.println(e.getClass().getName() + ": " + e.getMessage());
        }
    }


    private String getSessionIdFromCookie(HttpExchange httpExchange) {
        String session = getSessionIdCookie(httpExchange).get().getValue();
        String[] parts = session.split("\"");
        String sessionId = parts[1];
        return sessionId;
    }

    public String getUserNameFromCookie(HttpExchange httpExchange) {
        String cookieStr = getUserNameCookie(httpExchange).get().getValue();
        String[] parts = cookieStr.split("\"");
        String userName = parts[1];
        return userName;
    }

    private Optional<HttpCookie> getSessionIdCookie(HttpExchange httpExchange){
        String cookieStr = httpExchange.getRequestHeaders().getFirst("Cookie");
        List<HttpCookie> cookies = cookieHelper.parseCookies(cookieStr);
        return cookieHelper.findCookieByName(SESSION_COOKIE_NAME, cookies);
    }

    public Optional<HttpCookie> getUserNameCookie(HttpExchange httpExchange) {
        String cookieStr = httpExchange.getRequestHeaders().getFirst("Cookie");
        List<HttpCookie> cookies = cookieHelper.parseCookies(cookieStr);
        return cookieHelper.findCookieByName("username", cookies);
    }


    private static Map<String, String> parseFormData(String formData) throws UnsupportedEncodingException {
        Map<String, String> map = new HashMap<>();
        String[] pairs = formData.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            // We have to decode the value because it's urlencoded. see: https://en.wikipedia.org/wiki/POST_(HTTP)#Use_for_submitting_web_forms
            String value = new URLDecoder().decode(keyValue[1], "UTF-8");
            map.put(keyValue[0], value);
        }
        return map;
    }

    private void sendResponse(HttpExchange httpExchange, String response) throws IOException {
        httpExchange.sendResponseHeaders(200, response.length());
        OutputStream os = httpExchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    private static Optional<Boolean> findUserInDatabase(String userName, String password) {
        Connection c = null;
        PreparedStatement pstmt = null;
        try {
            Class.forName("org.postgresql.Driver");
            c = DriverManager.getConnection("jdbc:postgresql://localhost:5432/si4logindb", "konrad", "1234");
            pstmt = c.prepareStatement("SELECT * FROM users WHERE username LIKE ? AND password LIKE ?;");
            pstmt.setString(1, userName);
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int id = rs.getInt("id");
                userName = rs.getString("username");
                password = rs.getString("password");
                return Optional.of(true);
            } else {
                return Optional.of(false);
            }

        } catch (SQLException e) {
            System.err.println(e.getClass().getName() + ": " + e.getMessage());
            System.exit(0);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                pstmt.close();
                c.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return Optional.empty();
    }

    public boolean userHasActiveSessionInDatabase(String userName, String sessionId) {
        Connection c = null;
        PreparedStatement pstmt = null;
        try {
            Class.forName("org.postgresql.Driver");
            c = DriverManager.getConnection("jdbc:postgresql://localhost:5432/si4logindb", "konrad", "1234");
            pstmt = c.prepareStatement("SELECT * FROM sessioninfo WHERE username LIKE ? AND sessionid LIKE ?;");
            pstmt.setString(1, userName);
            pstmt.setString(2, sessionId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return true;
            } else {
                return false;
            }
        } catch (SQLException e) {
            System.err.println(e.getClass().getName() + ": " + e.getMessage());
            System.exit(0);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                pstmt.close();
                c.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return false;
    }
}
