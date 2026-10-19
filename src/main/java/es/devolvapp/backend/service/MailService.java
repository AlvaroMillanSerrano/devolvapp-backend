package es.devolvapp.backend.service;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MailService {
    private final JavaMailSender mailSender;

    @Value("$" + "{app.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Value("$" + "{spring.mail.username:}")
    private String fromAddress;

    @Value("$" + "{spring.mail.host:}")
    private String mailHost;

    public void sendPasswordResetLink(String email, String token) {
        String link = frontendBaseUrl + "/restablecer-password?token=" + token;
        if (mailHost == null || mailHost.isBlank()) {
            log.info("SMTP is not configured. Development password reset link for {}: {}", email, link);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        if (fromAddress != null && !fromAddress.isBlank()) message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("Restablece tu contraseña de DevolvUp");
        message.setText("Solicitaste restablecer la contraseña de tu cuenta. El enlace caduca en 30 minutos:\n\n"
                + link + "\n\nSi no solicitaste este cambio, puedes ignorar este correo.");
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            log.error("Could not send password reset email to {}", email, exception);
        }
    }
}
