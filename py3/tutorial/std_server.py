import sys
from typing import cast

from tutorial.generated.contract import acceptor
from tutorial.socket_client import serializer
from tutorial.socket_server import EchoServiceImpl, InstrumentServiceImpl
from yass import Server, Serializer, Writer, MessageSerializer, Reader, Request


def stdServer(server: Server, contractSerializer: Serializer):
    sout = sys.stdout.buffer
    sin = sys.stdin.buffer

    def writeBytes(value: bytes) -> None:
        sent = 0
        while sent < len(value):
            written = sout.write(value[sent:])
            if written == 0:
                raise RuntimeError("pipe connection broken")
            sent += written

    def readBytes(length: int) -> bytes:
        buffer = b''
        while len(buffer) < length:
            chunk = sin.read(length - len(buffer))
            if len(chunk) == 0:
                raise RuntimeError("socket connection broken")
            buffer += chunk
        return buffer

    messageSerializer = MessageSerializer(contractSerializer)
    writer = Writer(writeBytes)
    reader = Reader(readBytes)
    while True:
        messageSerializer.write(server.invoke(cast(Request, messageSerializer.read(reader))), writer)
        sout.flush()


stdServer(
    Server([
        acceptor.echoService.service(EchoServiceImpl()),
        acceptor.instrumentService.service(InstrumentServiceImpl())
    ]),
    serializer
)
