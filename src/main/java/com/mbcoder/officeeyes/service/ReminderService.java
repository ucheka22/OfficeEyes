package com.mbcoder.officeeyes.service;

import com.mbcoder.officeeyes.model.slack.Reminder;
import com.mbcoder.officeeyes.model.slack.SlackRequest;
import com.mbcoder.officeeyes.model.slack.SlackResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReminderService {

    public static final Logger LOGGER = LoggerFactory.getLogger(ReminderService.class);

    @Autowired
    SensorService sensorService;

    private HttpHeaders headers = new HttpHeaders();

    private RestTemplate restTemplate = new RestTemplate();

    private List<Reminder> reminders;

    public ReminderService() {
        headers.setContentType(MediaType.APPLICATION_JSON);
        reminders = new ArrayList<>();
    }

    public ReminderService(List<Reminder> reminders) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        this.reminders = reminders;
    }

    @Scheduled(fixedRateString = "10000")
    public void checkReminders() {
        if (reminders.size() > 0) {
            Instant now = Instant.now();
            // Remove the reminder if it is added more than 30 minutes ago.
            // This is because responseUrl is only valid for 30 minutes.
            reminders.removeIf(r -> Duration.between(r.getDate(), now).toMinutes() > 30);
            if (sensorService.isFree()) {
                reminders.forEach(this::sendReminder);
                reminders.clear();
            }
        }
    }

    public String addReminder(SlackRequest slackRequest) {
        Reminder reminder = new Reminder(Instant.now(), slackRequest);
        if (reminders.contains(reminder)) {
            // Remove the old reminder and add the new one for updated time.
            // Deleted and added reminders are equals in means of request but not time.
            // See Reminder.equals() for more info.
            reminders.remove(reminder);
            reminders.add(reminder);
            return "This reminder was already registered but I have extended it's duration!";
        }
        this.reminders.add(reminder);
        return "New reminder registered. I will inform you when the table is free.";
    }

    public void sendReminder(Reminder reminder) {
        SlackResponse response = new SlackResponse("Table is free now, hurry!");
        response.setResponseType(reminder.getRequest().getResponseType());
        HttpEntity<SlackResponse> entity = new HttpEntity<>(response, headers);
        LOGGER.debug("Sending reminder to Slack at url: {}", reminder.getRequest().getResponseUrl());
        ResponseEntity<String> answer = restTemplate.exchange(reminder.getRequest().getResponseUrl(), HttpMethod.POST, entity, String.class);
        LOGGER.debug("Slack response to the reminder request: {}", answer.getStatusCodeValue());
    }
}
