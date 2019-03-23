import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.sql.*;
import java.util.List;
import java.util.Optional;

public class Logged implements HttpHandler {
    private static final String SESSION_COOKIE_NAME = "sessionId";
    int counter = 0;
    CookieHelper cookieHelper = new CookieHelper();
    Login login = new Login();

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String method = httpExchange.getRequestMethod();

        if(method.equals("GET")) {
            System.out.println("GET Logged.java");

            Optional<HttpCookie> sessionIdCookie = getSessionIdCookie(httpExchange);
            if (sessionIdCookie.isPresent()) {
                Optional<HttpCookie> userNameCookie = login.getUserNameCookie(httpExchange);

                if (userNameCookie.isPresent()) {
                    String userName = login.getUserNameFromCookie(httpExchange);
                    String sessionId = getSessionIdFromCookie(httpExchange);

                    if (login.userHasActiveSessionInDatabase(userName, sessionId)) {
                        System.out.println("User has active session in database.    Logged in.");
                        counter++;
                        String response = "<html><body>" +
                                "<form method=\"POST\">\n" +
                                "  <input type=\"submit\" value=\"LOG OUT\">\n" +
                                "</form> Page was visited: " + counter + " times!";
                        sendResponse(httpExchange, response);
                    } else {
                        System.out.println("No such session in database! Redirecting to login page.");
                        httpExchange.getResponseHeaders().set("Location", "/login");
                        httpExchange.sendResponseHeaders(302,0);
                    }
                } else {
                    System.out.println("No cookie with username found! Redirecting to login page.");
                    httpExchange.getResponseHeaders().set("Location", "/login");
                    httpExchange.sendResponseHeaders(302,0);
                }
            } else {
                System.out.println("No cookie with sessionId found! Redirecting to login page.");
                httpExchange.getResponseHeaders().set("Location", "/login");
                httpExchange.sendResponseHeaders(302,0);
            }
        }

        if(method.equals("POST")) {
            System.out.println("POST Logged.java");
            System.out.println("logging out");
            String sessionId = getSessionIdFromCookie(httpExchange);
            deleteSessionIdFromDatabase(sessionId);
            System.out.println("User logged out, redirecting to login page");
            httpExchange.getResponseHeaders().set("Location", "/login");
            httpExchange.sendResponseHeaders(302,0);
        }
    }

    private Optional<HttpCookie> getSessionIdCookie(HttpExchange httpExchange){
        String cookieStr = httpExchange.getRequestHeaders().getFirst("Cookie");
        List<HttpCookie> cookies = cookieHelper.parseCookies(cookieStr);
        return cookieHelper.findCookieByName(SESSION_COOKIE_NAME, cookies);
    }

    private void sendResponse(HttpExchange httpExchange, String response) throws IOException {
        httpExchange.sendResponseHeaders(200, response.length());
        OutputStream os = httpExchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    private String getSessionIdFromCookie(HttpExchange httpExchange) {
        String session = getSessionIdCookie(httpExchange).get().getValue();
        String[] parts = session.split("\"");
        String sessionId = parts[1];
        return sessionId;
    }

    private static void deleteSessionIdFromDatabase (String sessionId) {
        Connection c = null;
        PreparedStatement pstmt = null;
        try {
            Class.forName("org.postgresql.Driver");
            c = DriverManager.getConnection("jdbc:postgresql://localhost:5432/si4logindb", "konrad", "1234");
            System.out.println("Opened database successfully");
            pstmt = c.prepareStatement("DELETE FROM sessioninfo WHERE sessionid LIKE ?");
            pstmt.setString(1, sessionId);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println(e.getClass().getName() + ": " + e.getMessage());
            System.exit(0);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                pstmt.close();
                c.close();
                System.out.println("Deleted sessionid successfully");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
