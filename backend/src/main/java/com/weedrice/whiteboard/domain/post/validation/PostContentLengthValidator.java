package com.weedrice.whiteboard.domain.post.validation;

import com.weedrice.whiteboard.domain.post.constant.PostContentConstraints;
import com.weedrice.whiteboard.domain.post.support.PostContentCodec;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PostContentLengthValidator implements ConstraintValidator<ValidPostContentLength, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return PostContentCodec.expandPreservedHtml(value).length() <= PostContentConstraints.MAX_SOURCE_LENGTH;
    }
}
