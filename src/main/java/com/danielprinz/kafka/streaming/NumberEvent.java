package com.danielprinz.kafka.streaming;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class NumberEvent {

  private long value;
  //Epoch second
  private long created;
  private String data;

}
