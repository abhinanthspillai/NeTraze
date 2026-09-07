import uuid
from pydantic import BaseModel, EmailStr, ConfigDict, field_validator

MIN_PASSWORD_LENGTH = 8
MAX_PASSWORD_LENGTH = 128


def validate_password_value(value: str) -> str:
    if value is None or value.strip() == "":
        raise ValueError("Password cannot be blank")
    if len(value) < MIN_PASSWORD_LENGTH or len(value) > MAX_PASSWORD_LENGTH:
        raise ValueError(
            f"Password must be between {MIN_PASSWORD_LENGTH} and {MAX_PASSWORD_LENGTH} characters"
        )
    return value


class LoginRequest(BaseModel):
    email: EmailStr
    password: str


class UserAuthProfile(BaseModel):
    id: uuid.UUID
    email: str
    role: str

    model_config = ConfigDict(from_attributes=True)


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserAuthProfile


class UserCreateRequest(BaseModel):
    email: EmailStr
    password: str
    role: str

    @field_validator("password")
    @classmethod
    def validate_password(cls, v: str) -> str:
        return validate_password_value(v)

    @field_validator("role")
    @classmethod
    def validate_role(cls, v: str) -> str:
        valid_roles = {"administrator", "user"}
        role_clean = v.lower().strip()
        if role_clean not in valid_roles:
            raise ValueError(f"Invalid role: '{v}'. Must be one of {valid_roles}")
        return role_clean


class PublicRegistrationRequest(BaseModel):
    email: EmailStr
    password: str
    confirm_password: str

    @field_validator("password")
    @classmethod
    def validate_password(cls, v: str) -> str:
        return validate_password_value(v)

    @field_validator("confirm_password")
    @classmethod
    def passwords_match(cls, v: str, info):
        if "password" in info.data and v != info.data["password"]:
            raise ValueError("Passwords do not match")
        return v


class ResetPasswordRequest(BaseModel):
    target_email: EmailStr
    new_password: str

    @field_validator("new_password")
    @classmethod
    def validate_new_password(cls, v: str) -> str:
        return validate_password_value(v)


class ResetPasswordResponse(BaseModel):
    message: str
    target_email: str
