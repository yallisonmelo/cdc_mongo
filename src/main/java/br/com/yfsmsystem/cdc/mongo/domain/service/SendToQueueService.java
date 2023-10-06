package br.com.yfsmsystem.cdc.mongo.domain.service;

import br.com.yfsmsystem.cdc.mongo.domain.dto.Customer;
import org.springframework.stereotype.Service;

@Service
public class SendToQueueService {

    //Send Value to Queue

    public void sendToQueue(Customer customer) {
        System.out.println("sendToQueue");
        System.out.println("Name: " + customer.firstName());
        System.out.println("Last Name: " + customer.lastName());
    }
}
