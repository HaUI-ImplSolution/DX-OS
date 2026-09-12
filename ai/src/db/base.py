from sqlalchemy.ext.declarative import DeferredReflection
from sqlalchemy.orm import DeclarativeBase


class Base(DeclarativeBase):
    pass


class ReflectedBase(Base, DeferredReflection):
    __abstract__ = True
