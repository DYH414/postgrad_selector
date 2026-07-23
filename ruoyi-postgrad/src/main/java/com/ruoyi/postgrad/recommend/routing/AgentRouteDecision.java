package com.ruoyi.postgrad.recommend.routing;

public record AgentRouteDecision(AgentIntentRoute route, String reason) {

    public AgentRouteDecision {
        route = route == null ? AgentIntentRoute.FALLBACK : route;
        reason = reason == null ? "" : reason;
    }

    public static AgentRouteDecision fallback(String reason) {
        return new AgentRouteDecision(AgentIntentRoute.FALLBACK, reason);
    }
}
