package com.promoteur.app.service.impl;

import com.promoteur.app.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    /** The console is French-only: labels are always resolved against messages_fr.properties. */
    private static final Locale LOCALE = Locale.FRENCH;

    private final MessageSource messageSource;

    @Override
    public String get(final String key, final Object... args) {
        return this.messageSource.getMessage(key, args, LOCALE);
    }
}
