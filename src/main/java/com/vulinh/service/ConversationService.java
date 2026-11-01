package com.vulinh.service;

import static com.vulinh.service.CoordinationValidation.checkCapacity;
import static com.vulinh.service.CoordinationValidation.validate;

import module java.base;

import com.vulinh.data.Answer;
import com.vulinh.data.Question;
import lombok.Locked;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Owns conversations; nested locking is always conversation lock then agent registry lock. */
@Service
@RequiredArgsConstructor
public class ConversationService {

  private static final int MAX_ENTRIES = 8912;
  private static final int MAX_TEXT_LENGTH = 32768;

  private final AgentRegistry agentRegistry;

  @SuppressWarnings("unused")
  private final ReadWriteLock conversationsLock = new ReentrantReadWriteLock();

  // Guarded together so pending questions and answers form a consistent view.
  private final Map<UUID, Question> questions = new LinkedHashMap<>();
  private final Map<UUID, Answer> answers = new LinkedHashMap<>();

  private final Map<UUID, Instant> questionCreatedAt = new LinkedHashMap<>();

  @Locked.Write("conversationsLock")
  public Question askQuestion(UUID from, UUID to, UUID requestId, String text) {
    agentRegistry.recordActivity(from);
    agentRegistry.requireAgent(to);

    if (requestId == null) {
      throw new IllegalArgumentException("requestId must be a UUID");
    }

    validate(text, "text", MAX_TEXT_LENGTH);

    for (var question : questions.values()) {
      if (question.from().equals(from) && question.requestId().equals(requestId)) {
        requireQuestion(question.id());

        if (!question.to().equals(to) || !question.text().equals(text)) {
          throw new IllegalArgumentException("requestId already used with different question data");
        }

        return question;
      }
    }

    checkCapacity(questions.size(), MAX_ENTRIES, "Question");

    var question =
        Question.builder()
            .id(UUID.randomUUID())
            .requestId(requestId)
            .from(from)
            .to(to)
            .text(text)
            .build();

    questions.put(question.id(), question);

    questionCreatedAt.put(question.id(), Instant.now());

    return question;
  }

  @Locked.Write("conversationsLock")
  public Answer answerQuestion(UUID agentId, UUID questionId, String text) {
    agentRegistry.recordActivity(agentId);

    var question = requireQuestion(questionId);

    if (!question.to().equals(agentId)) {
      throw new IllegalArgumentException("Only the question recipient may answer");
    }

    validate(text, "text", MAX_TEXT_LENGTH);

    var existing = answers.get(questionId);

    if (existing != null) {
      if (!existing.text().equals(text)) {
        throw new IllegalArgumentException("Question already has a different answer");
      }

      return existing;
    }

    checkCapacity(answers.size(), MAX_ENTRIES, "Answer");

    var answer = Answer.builder().questionId(questionId).from(agentId).text(text).build();

    answers.put(questionId, answer);

    return answer;
  }

  @Locked.Read("conversationsLock")
  public List<Question> pollQuestion(UUID agentId) {
    agentRegistry.recordActivity(agentId);

    return questions.values().stream()
        .filter(question ->
            !isExpired(question.id())
                && question.to().equals(agentId)
                && !answers.containsKey(question.id()))
        .toList();
  }

  @Locked.Read("conversationsLock")
  public List<Answer> pollAnswer(UUID agentId, UUID questionId) {
    agentRegistry.recordActivity(agentId);

    var question = requireQuestion(questionId);

    if (!question.from().equals(agentId)) {
      throw new IllegalArgumentException("Only the question sender may poll its answer");
    }

    var answer = answers.get(questionId);

    return answer == null ? Collections.emptyList() : Collections.singletonList(answer);
  }

  @Locked.Write("conversationsLock")
  public void invalidateUnansweredQuestions() {
    questions.values().removeIf(question -> isExpired(question.id()));
    questionCreatedAt.keySet().retainAll(questions.keySet());
  }

  @Locked.Write("conversationsLock")
  public void evictExpiredEntries() {
    invalidateUnansweredQuestions();

    var evicted = agentRegistry.evictIdleAgents();

    questions
        .values()
        .removeIf(question -> evicted.contains(question.from()) || evicted.contains(question.to()));

    answers.keySet().retainAll(questions.keySet());
    questionCreatedAt.keySet().retainAll(questions.keySet());
  }

  // Caller holds conversationsLock for reading or writing.
  private Question requireQuestion(UUID id) {
    var question = questions.get(id);

    if (question == null || isExpired(id)) {
      throw new IllegalArgumentException("Unknown question: %s".formatted(id));
    }

    return question;
  }

  private boolean isExpired(UUID id) {
    return !answers.containsKey(id)
        && !questionCreatedAt.get(id).plus(Duration.ofMinutes(10)).isAfter(Instant.now());
  }
}
