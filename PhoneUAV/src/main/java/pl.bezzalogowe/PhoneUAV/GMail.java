package pl.bezzalogowe.PhoneUAV;

import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Properties;

import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.Multipart;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.activation.DataHandler;


public class GMail {
    final String smtpAuth = "true";
    final String starttls = "true";

    String fromEmail;
    String fromPassword;
    String emailHost;
    int emailPort = 465;
    List<String> toEmailList;
    String emailSubject;
    String emailBody;

    Properties emailProperties;
    Session mailSession;
    MimeMessage emailMessage;
    MainActivity main;

    public GMail() {
    }

    public GMail(String host, int port, String from, String password, List<String> recipients, String subject, String body, MainActivity argActivity) {
        this.emailHost = host;
        this.emailPort = port;
        this.fromEmail = from;
        this.fromPassword = password;
        this.toEmailList = recipients;
        this.emailSubject = subject;
        this.emailBody = body;
        main = argActivity;

        emailProperties = System.getProperties();
        emailProperties.put("mail.smtp.port", emailPort);
        emailProperties.put("mail.smtp.auth", smtpAuth);
        emailProperties.put("mail.smtp.starttls.enable", starttls);
        Log.i("GMail", "Mail server properties set.");
    }

    public MimeMessage createEmailMessage() throws MessagingException, AddressException, UnsupportedEncodingException {

        mailSession = Session.getDefaultInstance(emailProperties, null);
        emailMessage = new MimeMessage(mailSession);

        emailMessage.setFrom(new InternetAddress(fromEmail, fromEmail));
        for (String toEmail : toEmailList) {
            Log.i("GMail", "toEmail: " + toEmail);
            emailMessage.addRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
        }

        emailMessage.setSubject(emailSubject);

        /* adding attachment */
        /** https://www.tutorialspoint.com/javamail_api/javamail_api_send_email_with_attachment.htm */

        BodyPart messageBodyPart = new MimeBodyPart();
        messageBodyPart.setText(emailBody);
        //Log.i("GMail", "email body: " + emailBody);
        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(messageBodyPart);
        BodyPart messageAttachmentPart = new MimeBodyPart();
        DataSource source = new FileDataSource(main.camObjectLolipop.imageFilePath);
        //Log.i("GMail", "attachment file path: " + main.camObjectLolipop.imageFilePath);
        messageAttachmentPart.setDataHandler(new DataHandler(source));
        messageAttachmentPart.setFileName(main.camObjectLolipop.imageFileName);
        multipart.addBodyPart(messageAttachmentPart);

        emailMessage.setContent(multipart);
        // for a email with attachment
        // emailMessage.setContent(emailBody, "text/html");
        // for a html email
        // emailMessage.setText(emailBody);
        // for a text email
        Log.i("GMail", "Email Message created.");
        return emailMessage;
    }

    public void sendEmail() throws MessagingException, AddressException {
        Transport transport = mailSession.getTransport("smtp");
        //System.out.println(emailHost+" "+fromEmail+" "+fromPassword);
        //FIXME: AuthenticationFailedException
        transport.connect(emailHost, fromEmail, fromPassword);
        Log.i("GMail", "allrecipients: " + emailMessage.getAllRecipients());
        transport.sendMessage(emailMessage, emailMessage.getAllRecipients());
        transport.close();
        Log.i("GMail", "Email sent successfully.");
    }
}
