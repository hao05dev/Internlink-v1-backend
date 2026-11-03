package com.internlink.core.application.system.notification;

import com.internlink.core.infrastructure.integration.email.EmailProperties;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.unbescape.html.HtmlEscape;

import java.time.Year;
import java.util.*;
import java.util.regex.Pattern;

/** Render email bằng Thymeleaf: HTML mode cho bản HTML, TEXT mode cho bản text. */
@Component
public class EmailRenderer {
    private static final Pattern TITLE = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL);
    private static final Locale LOCALE = Locale.forLanguageTag("vi-VN");
    private final TemplateEngine engine = engine();
    private final EmailProperties settings;

    public EmailRenderer(EmailProperties settings) {
        this.settings = settings;
    }

    public RenderedEmail render(EmailTemplate template, Map<String, ?> variables) {
        var missing =
                template.variables().stream().filter(name -> !variables.containsKey(name)).sorted().toList();
        if (!missing.isEmpty())
            throw new IllegalArgumentException(
                    "Thiếu biến cho email " + template.file() + ": " + missing);
        var context = new Context(LOCALE);
        variables.forEach((name, value) -> context.setVariable(name, value == null ? "" : value));
        context.setVariable("baseUrl", baseUrl());
        context.setVariable("year", Year.now().getValue());
        String html = engine.process("email/" + template.file(), context);
        String text =
                engine.process("email/" + template.file() + ".txt", context)
                        .replace("\r\n", "\n")
                        .replaceAll("\n{3,}", "\n\n")
                        .strip();
        var title = TITLE.matcher(html);
        if (!title.find())
            throw new IllegalStateException("Template " + template.file() + " thiếu thẻ title");
        String subject = HtmlEscape.unescapeHtml(title.group(1)).replaceAll("\\s+", " ").strip();
        if (subject.length() > 255) subject = subject.substring(0, 255);
        return new RenderedEmail(template.name(), subject, text, html);
    }

    public String baseUrl() {
        return settings.getPublicBaseUrl().replaceAll("/+$", "");
    }

    private static TemplateEngine engine() {
        var text = new ClassLoaderTemplateResolver();
        text.setPrefix("templates/");
        text.setTemplateMode(TemplateMode.TEXT);
        text.setCharacterEncoding("UTF-8");
        text.setResolvablePatterns(Set.of("*.txt"));
        text.setCheckExistence(true);
        text.setOrder(1);
        var html = new ClassLoaderTemplateResolver();
        html.setPrefix("templates/");
        html.setSuffix(".html");
        html.setTemplateMode(TemplateMode.HTML);
        html.setCharacterEncoding("UTF-8");
        html.setOrder(2);
        var engine = new TemplateEngine();
        engine.addTemplateResolver(text);
        engine.addTemplateResolver(html);
        return engine;
    }
}
