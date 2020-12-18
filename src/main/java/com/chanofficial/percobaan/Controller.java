package com.chanofficial.percobaan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.client.LineSignatureValidator;
import com.linecorp.bot.model.Multicast;
import com.linecorp.bot.model.PushMessage;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.message.StickerMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.objectmapper.ModelObjectMapper;
import com.linecorp.bot.model.profile.UserProfileResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static jdk.nashorn.internal.objects.NativeArray.push;



@RestController
public class Controller {

    @Autowired
    @Qualifier("lineMessagingClient")
    private LineMessagingClient lineMessagingClient;

    @Autowired
    @Qualifier("lineSignatureValidator")
    private LineSignatureValidator lineSignatureValidator;

    @RequestMapping(value="/webhook", method= RequestMethod.POST)
    public ResponseEntity<String> callback(
            @RequestHeader("X-Line-Signature") String xLineSignature,
            @RequestBody String eventsPayload)
    {

        try {
            if (!lineSignatureValidator.validateSignature(eventsPayload.getBytes(), xLineSignature)) {
                throw new RuntimeException("Invalid Signature Validation");
            }

            // parsing event
            ObjectMapper objectMapper = ModelObjectMapper.createNewObjectMapper();
            EventsModel eventsModel = objectMapper.readValue(eventsPayload, EventsModel.class);


                // kode reply message disini
                eventsModel.getEvents().forEach((event)->{
                    if (event instanceof MessageEvent) {
                        MessageEvent messageEvent = (MessageEvent) event;
                        TextMessageContent textMessageContent = (TextMessageContent) messageEvent.getMessage();
                        if(textMessageContent.getText().equalsIgnoreCase("userid")){
                            replyText(messageEvent.getReplyToken(), event.getSource().getUserId());
                        }
                        if(textMessageContent.getText().equalsIgnoreCase("halo")){
                            replyText(messageEvent.getReplyToken(), "halo juga :)");
                        }

                        /*   kode dibawah untuk auto reply message dari user dengan pesan yang sama
                        replyText(messageEvent.getReplyToken(), textMessageContent.getText());
                         */

                    }
                });


            return new ResponseEntity<>(HttpStatus.OK);


        } catch (IOException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }


    // PUSH MASSAGE
    // https://<nama_host>.herokuapp.com/pushmessage/id_bot/pesan
    // https://percobaan-line.herokuapp.com/pushmessage/U65928249e5b24f78b27709916ea3915d/halo
    @RequestMapping(value="/pushmessage/{id}/{message}", method=RequestMethod.GET)
    public ResponseEntity<String> pushmessage(
            @PathVariable("id") String userId,
            @PathVariable("message") String textMsg
    ){
        TextMessage textMessage = new TextMessage(textMsg);
        PushMessage pushMessage = new PushMessage(userId, textMessage);
        push(pushMessage);

        return new ResponseEntity<String>("Push message:"+textMsg+"\nsent to: "+userId, HttpStatus.OK);
    }

    // multicast massage
    // https://<nama_host>.herokuapp.com/multicast
    // https://percobaan-line.herokuapp.com/multicast
    @RequestMapping(value="/multicast", method=RequestMethod.GET)
    public ResponseEntity<String> multicast(){
        // Array Untuk user bot multicast pesan
        String[] userIdList = {
                "U65928249e5b24f78b27709916ea3915d"};
        Set<String> listUsers = new HashSet<String>(Arrays.asList(userIdList));
        if(listUsers.size() > 0){
            String textMsg = "ini adalah pesan multicast\njika pesan ini tampil artinya\nBERHASIL";
            sendMulticast(listUsers, textMsg);
        }
        return new ResponseEntity<String>(HttpStatus.OK);
    }

    // Profile API
    @RequestMapping(value = "/profile", method = RequestMethod.GET)
    public ResponseEntity<String> profile(){
        String userId = "U65928249e5b24f78b27709916ea3915d";
        UserProfileResponse profile = getProfile(userId);

        if (profile != null) {
            String profileName = profile.getDisplayName();
            TextMessage textMessage = new TextMessage("Hello, " + profileName);
            PushMessage pushMessage = new PushMessage(userId, textMessage);
            push(pushMessage);

            return new ResponseEntity<String>("Hello, "+profileName, HttpStatus.OK);
        }

        return new ResponseEntity<String>(HttpStatus.NOT_FOUND);
    }



    // Method Method yang diperlukan

    private void reply(ReplyMessage replyMessage) {
        try {
            lineMessagingClient.replyMessage(replyMessage).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }


    private void replyText(String replyToken, String messageToUser){
        TextMessage textMessage = new TextMessage(messageToUser);
        ReplyMessage replyMessage = new ReplyMessage(replyToken, textMessage);
        reply(replyMessage);
    }

    private void replySticker(String replyToken, String packageId, String stickerId){
        StickerMessage stickerMessage = new StickerMessage(packageId, stickerId);
        ReplyMessage replyMessage = new ReplyMessage(replyToken, stickerMessage);
        reply(replyMessage);
    }

    private void push(PushMessage pushMessage){
        try {
            lineMessagingClient.pushMessage(pushMessage).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendMulticast(Set<String> sourceUsers, String txtMessage){
        TextMessage message = new TextMessage(txtMessage);
        Multicast multicast = new Multicast(sourceUsers, message);

        try {
            lineMessagingClient.multicast(multicast).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private UserProfileResponse getProfile(String userId){
        try {
            return lineMessagingClient.getProfile(userId).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}