import type { ChangeEvent, FocusEvent, FormEvent } from 'react';
import { useState } from 'react';

import type { SignupInput } from '@/types/api';

// 가입 시 사용자 유형을 고르지 않습니다. 모든 회원은 소비자로 시작하고,
// 공간을 등록하면 공간 제공자, 매칭이 수락되면 농부 역할이 서버에서 더해집니다.
interface SignupFormValues {
  nickname: string;
  email: string;
  password: string;
  passwordConfirm: string;
}

type SignupTextField = 'nickname' | 'email' | 'password' | 'passwordConfirm';
type SignupFormErrors = Partial<Record<SignupTextField, string>>;

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const initialValues: SignupFormValues = {
  nickname: '',
  email: '',
  password: '',
  passwordConfirm: '',
};

function validateTextField(
  field: SignupTextField,
  value: string,
  values: SignupFormValues,
) {
  const trimmedValue = value.trim();

  if (field === 'nickname') {
    if (!trimmedValue) return '이름 또는 닉네임을 입력해 주세요.';
    if (trimmedValue.length < 2 || trimmedValue.length > 30) {
      return '이름 또는 닉네임은 2자 이상 30자 이하로 입력해 주세요.';
    }
    return undefined;
  }

  if (field === 'email') {
    if (!trimmedValue) return '이메일을 입력해 주세요.';
    if (!EMAIL_PATTERN.test(trimmedValue)) return '올바른 이메일 형식을 입력해 주세요.';
    return undefined;
  }

  if (field === 'password') {
    if (!value) return '비밀번호를 입력해 주세요.';
    if (value.length < 8) return '비밀번호는 8자 이상 입력해 주세요.';
    return undefined;
  }

  if (field === 'passwordConfirm') {
    if (!value) return '비밀번호 확인을 입력해 주세요.';
    if (value !== values.password) return '비밀번호가 일치하지 않습니다.';
    return undefined;
  }

  return undefined;
}

function validateForm(values: SignupFormValues) {
  const errors: SignupFormErrors = {};
  const textFields: SignupTextField[] = [
    'nickname',
    'email',
    'password',
    'passwordConfirm',
  ];

  textFields.forEach((field) => {
    const error = validateTextField(field, values[field], values);
    if (error) errors[field] = error;
  });

  return errors;
}

interface SignupFormOptions {
  // 서버도 가입 시점에 다시 확인하지만, 여기서 먼저 막아야 사용자가 이유를 바로 알 수 있습니다.
  //
  // 불리언이 아니라 함수로 받습니다. 인증 여부는 이 훅이 소유한 이메일 값에 달려 있는데,
  // 값을 받아 계산한 불리언을 넘기려면 호출부가 values를 먼저 알아야 해 선언 순서가 순환합니다.
  isEmailVerified: (email: string) => boolean;
}

export function useSignupForm(
  onSubmit: (values: SignupInput) => Promise<void>,
  options: SignupFormOptions,
) {
  const [values, setValues] = useState<SignupFormValues>(initialValues);
  const [errors, setErrors] = useState<SignupFormErrors>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const hasErrors = Object.values(errors).some(Boolean);

  function handleTextChange(event: ChangeEvent<HTMLInputElement>) {
    const field = event.currentTarget.name as SignupTextField;
    const value = event.currentTarget.value;
    const nextValues = { ...values, [field]: value };
    setValues(nextValues);

    if (errors[field]) {
      setErrors((current) => ({
        ...current,
        [field]: validateTextField(field, value, nextValues),
      }));
    }

    if (field === 'password' && errors.passwordConfirm && values.passwordConfirm) {
      setErrors((current) => ({
        ...current,
        passwordConfirm: validateTextField(
          'passwordConfirm',
          values.passwordConfirm,
          nextValues,
        ),
      }));
    }
  }

  function handleBlur(event: FocusEvent<HTMLInputElement>) {
    const field = event.currentTarget.name as SignupTextField;
    const error = validateTextField(field, event.currentTarget.value, values);
    setErrors((current) => ({ ...current, [field]: error }));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors = validateForm(values);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    // 인증을 안 했다고 제출 버튼을 계속 비활성화하면 왜 못 누르는지 알 수 없습니다.
    // 누를 수는 있게 두고, 눌렀을 때 이유를 밝힙니다.
    if (!options.isEmailVerified(values.email)) {
      setSubmitError('이메일 인증을 완료해 주세요.');
      return;
    }

    setSubmitError(null);
    setIsSubmitting(true);
    try {
      await onSubmit({
        nickname: values.nickname.trim(),
        email: values.email.trim(),
        password: values.password,
      });
    } catch (caught) {
      setSubmitError(
        caught instanceof Error ? caught.message : '회원가입에 실패했습니다.',
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  return {
    values,
    errors,
    hasErrors,
    isSubmitting,
    submitError,
    handleTextChange,
    handleBlur,
    handleSubmit,
  };
}
