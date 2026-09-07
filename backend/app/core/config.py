from typing import List
from pydantic import model_validator, validator
from pydantic_settings import BaseSettings, SettingsConfigDict


DEVELOPMENT_SECRET_KEY = "netraze_development_secret_key_change_in_production_987654321"


class Settings(BaseSettings):
    PROJECT_NAME: str = "Netraze Backend"
    API_V1_STR: str = "/api/v1"
    ENVIRONMENT: str = "development"
    DEBUG: bool = True

    # Database connection. Local development keeps a convenient fallback;
    # deployed environments should provide DATABASE_URL explicitly.
    DATABASE_URL: str = "postgresql+psycopg://netraze_app:1234@127.0.0.1:5432/netraze"

    @validator("DATABASE_URL", pre=True)
    def assemble_db_connection(cls, v: str) -> str:
        if isinstance(v, str):
            if v.startswith("postgres://"):
                return v.replace("postgres://", "postgresql+psycopg://", 1)
            if v.startswith("postgresql://"):
                return v.replace("postgresql://", "postgresql+psycopg://", 1)
        return v

    # JWT authentication security settings.
    # A known development fallback is allowed only outside production.
    SECRET_KEY: str = DEVELOPMENT_SECRET_KEY
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 480

    # CORS
    BACKEND_CORS_ORIGINS: List[str] = [
        "http://localhost:8000",
        "http://127.0.0.1:8000"
    ]

    @model_validator(mode="after")
    def validate_production_security(self):
        environment = self.ENVIRONMENT.strip().lower()
        if environment in {"production", "prod"}:
            if self.SECRET_KEY == DEVELOPMENT_SECRET_KEY or len(self.SECRET_KEY) < 32:
                raise ValueError(
                    "Production requires a strong SECRET_KEY supplied through environment configuration."
                )
            if self.DEBUG:
                raise ValueError("DEBUG must be false in production.")
            if "127.0.0.1" in self.DATABASE_URL or "localhost" in self.DATABASE_URL:
                raise ValueError("Production requires DATABASE_URL to be supplied through environment configuration.")
        return self

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore"
    )


settings = Settings()
