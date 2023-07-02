package ru.practicum.main_service.comment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.main_service.comment.dto.CommentDto;
import ru.practicum.main_service.comment.dto.NewCommentDto;
import ru.practicum.main_service.comment.mapper.CommentMapper;
import ru.practicum.main_service.comment.model.Comment;
import ru.practicum.main_service.comment.repository.CommentRepository;
import ru.practicum.main_service.event.enums.EventState;
import ru.practicum.main_service.event.model.Event;
import ru.practicum.main_service.event.service.EventService;
import ru.practicum.main_service.exception.ForbiddenException;
import ru.practicum.main_service.exception.NotFoundException;
import ru.practicum.main_service.user.model.User;
import ru.practicum.main_service.user.service.UserService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class CommentServiceImpl implements CommentService {
    private final UserService userService;
    private final EventService eventService;
    private final CommentRepository commentRepository;
    private final CommentMapper commentMapper;

    @Override
    public List<CommentDto> getCommentsByAdmin(Pageable pageable) {
        return toCommentsDto(commentRepository.findAll(pageable).toList());
    }

    @Override
    @Transactional
    public void deleteByAdmin(Long commentId) {
        commentRepository.deleteById(commentId);
    }

    @Override
    public List<CommentDto> getCommentsByPrivate(Long userId, Long eventId, Pageable pageable) {
        userService.getUserById(userId);

        List<Comment> comments;
        if (eventId != null) {
            eventService.getEventById(eventId);

            comments = commentRepository.findAllByAuthorIdAndEventId(userId, eventId);
        } else {
            comments = commentRepository.findAllByAuthorId(userId);
        }

        return toCommentsDto(comments);
    }

    @Override
    @Transactional
    public CommentDto createByPrivate(Long userId, Long eventId, NewCommentDto newCommentDto) {
        User user = userService.getUserById(userId);
        Event event = eventService.getEventById(eventId);

        if (!event.getState().equals(EventState.PUBLISHED)) {
            throw new ForbiddenException("Forbidden: event yet not published");
        }

        Comment comment = Comment.builder()
                .text(newCommentDto.getText())
                .author(user)
                .event(event)
                .createdOn(LocalDateTime.now())
                .build();

        return commentMapper.toCommentDto(commentRepository.save(comment));
    }

    @Override
    @Transactional
    public CommentDto patchByPrivate(Long userId, Long commentId, NewCommentDto newCommentDto) {
        userService.getUserById(userId);

        Comment commentFromRepository = getCommentById(commentId);

        checkUserIsOwner(userId, commentFromRepository.getAuthor().getId());

        commentFromRepository.setText(newCommentDto.getText());
        commentFromRepository.setEditedOn(LocalDateTime.now());

        return commentMapper.toCommentDto(commentRepository.save(commentFromRepository));
    }

    @Override
    @Transactional
    public void deleteByPrivate(Long userId, Long commentId) {
        userService.getUserById(userId);

        checkUserIsOwner(userId, getCommentById(commentId).getAuthor().getId());

        commentRepository.deleteById(commentId);
    }

    @Override
    public List<CommentDto> getCommentsByPublic(Long eventId, Pageable pageable) {
        eventService.getEventById(eventId);

        return toCommentsDto(commentRepository.findAllByEventId(eventId, pageable));
    }

    @Override
    public CommentDto getCommentByPublic(Long commentId) {
        return commentMapper.toCommentDto(getCommentById(commentId));
    }

    private List<CommentDto> toCommentsDto(List<Comment> comments) {
        return comments.stream()
                .map(commentMapper::toCommentDto)
                .collect(Collectors.toList());
    }

    private Comment getCommentById(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("No comment with ID: " + commentId));
    }

    private void checkUserIsOwner(Long id, Long userId) {
        if (!Objects.equals(id, userId)) {
            throw new ForbiddenException("Forbidden: user is not an owner");
        }
    }
}
