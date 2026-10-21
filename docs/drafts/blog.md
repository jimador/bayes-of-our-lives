# Bayes of Our Lives: Experimental Decision Models in Embabel Agent

*Like sands through the hourglass, so are the LLM API calls of our lives.*

You may or may not have heard of decision models. They're a newer kind of model, and they do a narrower job than the LLM you might already have judging things in your app. You give one a question, some state, and the answers it's allowed to give. It hands back one of those answers with probabilities attached. Here's a choice answer from [TypeSafe's quickstart](https://docs.typesafe.ai/introduction/quickstart), for a question about which team should handle a support ticket. The state is the ticket itself, a customer saying their API integration has been throwing 500 errors for twenty minutes and they can't process orders.

```json
{
  "model": "jev-1.13.0",
  "answers": {
    "department": {
      "type": "choice",
      "choice": "technical",
      "confidence": 1.0,
      "probabilities": { "technical": 1.0, "billing": 0.0, "sales": 0.0 }
    }
  }
}
```

The answer is `technical`, and the probabilities tell your code how sure it ought to be about that.

The big draw here is speed. A decision model doesn't have to write anything out. It just picks from the answers you gave it, so there's no long response to generate and no text to parse on the other end. Those savings add up fast when an agent has to make a bunch of small calls on the way to a result.

[Jev](https://typesafe.ai/blog/introducing-system-one-models-and-jev) is TypeSafe's first *System One* model. I wanted to see how it stacked up against an LLM making the same call inside a real agent, so I built a small app to find out.

## Decisions in Embabel Agent

[Embabel Agent](https://github.com/embabel/embabel-agent) is a JVM framework for building agents out of goals and actions. It plans a path through your actions based on the types they take and return. It can now call decision models too. An action asks a typed question through a `DecisionService` and gets back a typed answer.

## A layover in Tampa

The test app is [Bayes of Our Lives](https://github.com/jimador/bayes-of-our-lives). It's a stripped-down spin-off of [Tripper](https://github.com/embabel/tripper), Embabel's travel planner example. Tripper plans a whole trip. Bayes plans a few hours, because nobody needs a seven-day travel agent to figure out that lunch is missing.

The scenario is a long layover in Tampa. You've got from about 7 a.m. to 2 p.m., and you'd rather see some of the city than sit at the gate. It's you and a partner, one of you is into art, and you want breakfast and lunch. You're both up for seafood or Cuban food, and anything but Italian.

An LLM does the bulk of the work. It picks venues from a map search, researches them, and drafts a couple of itineraries. Then the app checks every draft against drive times, meal windows, and opening hours, and marks anything that doesn't fit as invalid. It even leaves ten minutes on every leg for parking, which is more planning than I usually do. The plans that pass go to the decision model, which makes the calls at the end. It picks the best plan, says whether that plan covers what the traveler asked for, and rates how confident it is in the pick.

## Role call

One thing I've always liked about Embabel is that your code never has to name a model. You give each model a role, like "cheapest" or "best," map the roles to real models in configuration, and your code asks for the role. Embabel's docs call it [role-based model selection](https://docs.embabel.com/embabel-agent/guide/1.5.0-SNAPSHOT/#setting-default-llm-and-roles), and they point out it's good practice to keep your code decoupled from specific models this way. Decision models work the same way now. Each engine in the app gets a role, and the roles are just properties. Here are the Jev and OpenAI lines from the app's configuration.

```properties
embabel.agent.platform.models.openai.api-key=${OPENAI_API_KEY:}
embabel.agent.platform.models.typesafe.api-key=${TYPESAFE_API_KEY:}
embabel.models.decision.roles.jev=jev-latest
embabel.models.decision.roles.openai=gpt-4.1
embabel.models.classification.roles.jev=jev-latest
embabel.models.classification.roles.openai=gpt-4.1
```

`jev` points at Jev, and `openai` points at a chat model that Embabel turns into a decision service for you. Each role is set for both decisions and classifications, so one model answers everything for a given run. Inside the action, Bayes looks the model up by role with `context.ai().decisions().byRole(role)`. Switching models is a property change.

## Call and response

Bayes asks three questions in one decision request and then a fourth as a classification request. The coverage question asks whether a plan covers every requested stop and meal. The next question asks which plan fits best, or whether to go research more. The confidence question rates the best plan as weak, possible, or strong. Readiness classifies the whole set of plans.

The `next` question is the interesting one, because its answers may change every time. The options are whichever plans passed validation, plus an escape hatch. In the code that list is `valid`. This method just builds the question, so no model gets called yet.

```java
static ChoiceQuestionSpec next(List<Itinerary.Alternative> valid) {
    var question =
            Questions.named("next")
                    .choice(
                            """
                            Which alternative best fits the traveller's profile, liked cuisines and timing? ...\
                            """);
    valid.forEach(a -> question.option(a.id(), a.title() + " (" + a.status() + ")"));
    return question.option("research", "Resolve material questions before choosing").build();
}
```

A plan that failed validation never makes the list, so neither model can pick it, and no LLM gets to talk a bad plan into a good one by sounding pleased with itself. Here's where the model actually gets called, whichever one the role points at. Asking all four questions looks like this.

```java
DecisionResponse response =
        decisions.ask(
                DecisionRequest.of(
                        input, DecisionSpec.of(COVERAGE, next, CONFIDENCE)));
...
ClassificationResult readiness =
        classifier.classify(ClassificationRequest.of(input, READINESS));
```

The answers come back typed. Each one is a sealed result with answered, inconclusive, and failure cases, so a switch over it is exhaustive, and the compiler tells you if you miss one. Here's the coverage answer.

```java
private static String coverage(PropositionResult result) {
    return switch (result) {
        case PropositionResult.Answered answered -> Boolean.toString(answered.getAnswer());
        case PropositionResult.Inconclusive inconclusive -> "inconclusive";
        case PropositionResult.Failure failure -> failure("coverage", failure.getReason());
    };
}
```

Bayes never has to parse text or guess what the model meant. If the model picks something that wasn't on the list, Bayes records that as a failure.

## Time will tell

Bayes times the decision call and the classification call separately, so it's easy to compare the two models on just the part they're responsible for. I ran the same Tampa request once with each.

| Model | Decision call | Classification call |
| --- | --- | --- |
| Jev | 416 ms | 141 ms |
| OpenAI | 736 ms | 603 ms |

Jev was faster on both calls, about 1.8 times on the decision and about 4 times on the classification. These are remote calls, so the numbers include the network, and one run each is a small sample. Still, the gap was clear for the exact same questions.

Both models picked the same plan, which was breakfast at Fast Jacks Breakfast, the Tampa Museum of Art, and a seafood lunch at Juicy Seafood. Not much of a cliffhanger, but nothing Italian made the cut.

Bayes also uses [Dice](https://github.com/embabel/dice) to remember interests and pace between outings, but that's a story for another episode.

## Your turn

The code is on GitHub at [jimador/bayes-of-our-lives](https://github.com/jimador/bayes-of-our-lives), and the README walks through setup. You'll need your own OpenAI and TypeSafe keys. Swap in whatever city your layover lands you in, flip the role, and watch the clock.

