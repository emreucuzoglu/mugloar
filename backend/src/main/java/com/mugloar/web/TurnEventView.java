package com.mugloar.web;

import com.mugloar.solver.TurnEvent;
import java.util.List;

record TurnEventView(TurnEvent.Type type, boolean success, String message, GameStateView state) {

  static TurnEventView of(TurnEvent event) {
    return new TurnEventView(
        event.type(), event.success(), event.message(), GameStateView.of(event.state()));
  }

  static List<TurnEventView> of(List<TurnEvent> events) {
    return events.stream().map(TurnEventView::of).toList();
  }
}
