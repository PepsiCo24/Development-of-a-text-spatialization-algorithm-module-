from dataclasses import dataclass


@dataclass(frozen=True)
class RuntimeProvider:
    base_url: str
    api_key: str
    model: str
    temperature: float
    prompt_template: str | None = None


_providers: dict[str, RuntimeProvider] = {}


def set_runtime_provider(name: str, provider: RuntimeProvider) -> None:
    _providers[name.lower()] = provider


def get_runtime_provider(name: str) -> RuntimeProvider | None:
    return _providers.get(name.lower())
