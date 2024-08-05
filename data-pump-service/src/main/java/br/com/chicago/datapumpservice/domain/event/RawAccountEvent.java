package br.com.chicago.datapumpservice.domain.event;

public interface RawAccountEvent {

    void notify(String message);

}
