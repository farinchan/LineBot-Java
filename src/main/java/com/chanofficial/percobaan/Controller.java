package com.chanofficial.percobaan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.client.LineSignatureValidator;
import com.linecorp.bot.client.MessageContentResponse;
import com.linecorp.bot.model.Multicast;
import com.linecorp.bot.model.PushMessage;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.*;
import com.linecorp.bot.model.event.source.GroupSource;
import com.linecorp.bot.model.event.source.RoomSource;
import com.linecorp.bot.model.message.FlexMessage;
import com.linecorp.bot.model.message.StickerMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.flex.container.FlexContainer;
import com.linecorp.bot.model.objectmapper.ModelObjectMapper;
import com.linecorp.bot.model.profile.UserProfileResponse;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
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

                    if (event.getSource() instanceof GroupSource || event.getSource() instanceof RoomSource) {
                        // dengan method
                        handleGroupRoomChats((MessageEvent) event);
                    } else {
                        // dengan method
                        handleOneOnOneChats((MessageEvent) event);
                    }
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
    // https://<nama_host>.herokuapp.com/profile
    // https://percobaan-line.herokuapp.com/profile
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

    @RequestMapping(value = "/content/{id}", method = RequestMethod.GET)
    public ResponseEntity content(
            @PathVariable("id") String messageId
    ){
        MessageContentResponse messageContent = getContent(messageId);

        if(messageContent != null) {
            HttpHeaders headers = new HttpHeaders();
            String[] mimeType = messageContent.getMimeType().split("/");
            headers.setContentType(new MediaType(mimeType[0], mimeType[1]));

            InputStream inputStream = messageContent.getStream();
            InputStreamResource inputStreamResource = new InputStreamResource(inputStream);

            return new ResponseEntity<>(inputStreamResource, headers, HttpStatus.OK);
        }

        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
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

    private MessageContentResponse getContent(String messageId) {
        try {
            return lineMessagingClient.getMessageContent(messageId).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

// Method Untuk reply message di private chat
    private void handleOneOnOneChats(MessageEvent event) {

        // Kode untuk reply message disini

            // reply content
            if  ((  (MessageEvent) event).getMessage() instanceof AudioMessageContent
            || ((MessageEvent) event).getMessage() instanceof ImageMessageContent
            || ((MessageEvent) event).getMessage() instanceof VideoMessageContent
            || ((MessageEvent) event).getMessage() instanceof FileMessageContent
                ) {
                    String baseURL     = "https://percobaan-line.herokuapp.com";
                    String contentURL  = baseURL+"/content/"+ ((MessageEvent) event).getMessage().getId();
                    String contentType = ((MessageEvent) event).getMessage().getClass().getSimpleName();
                    String textMsg     = contentType.substring(0, contentType.length() -14)
                            + " yang kamu kirim bisa diakses dari link:\n "
                            + contentURL;

                    replyText(((MessageEvent) event).getReplyToken(), textMsg);
                }

            // reply flex dan pesan
            else if (event.getMessage() instanceof TextMessageContent){
                TextMessageContent textMessageContent = (TextMessageContent) event.getMessage();
                if (textMessageContent.getText().toLowerCase().contains("flex")) {
                    replyFlexMessage(event.getReplyToken());
                } else {

                    if(textMessageContent.getText().equalsIgnoreCase("userid")){
                        replyText(event.getReplyToken(), event.getSource().getUserId());
                    }
                    if(textMessageContent.getText().equalsIgnoreCase("halo")){
                        replyText(event.getReplyToken(), "halo ada yang bisa saya bantu :)");
                        replyFlexMessage(event.getReplyToken());}

                    /*kode dibawah untuk reply message, kembalikan message yang dikirim
                    replyText(event.getReplyToken(), textMessageContent.getText());
                     */
                }

            }
            else {
                replyText(event.getReplyToken(), "Unknown Message");
                }
            }




// Method untuk reply message di grup
    private void handleGroupRoomChats(MessageEvent event) {
        if(!event.getSource().getUserId().isEmpty()) {
            String userId = event.getSource().getUserId();
            UserProfileResponse profile = getProfile(userId);
            replyText(event.getReplyToken(), "Hello, " + profile.getDisplayName());
        } else {
            replyText(event.getReplyToken(), "Hello, what is your name?");
        }
    }

    private void replyFlexMessage(String replyToken) {
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            String flexTemplate = IOUtils.toString(classLoader.getResourceAsStream("info.json"));


            ObjectMapper objectMapper = ModelObjectMapper.createNewObjectMapper();
            FlexContainer flexContainer = objectMapper.readValue(flexTemplate, FlexContainer.class);


            ReplyMessage replyMessage = new ReplyMessage(replyToken, new FlexMessage("info", flexContainer));
            reply(replyMessage);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}