package fr.uge.tools.trames;

public sealed interface Trame permits TryConnectionReader, 
                                      MsgToAllReader, MsgReceivedReader, MsgToOneReader, PseudoNotFoundReader, 
                                      ShareFilesReader, StopShareFilesReader,
                                      AvailablePseudosReader, AvailableFilesReader,
                                      RequestListSharerOrProxyReader, ListSharerReader, OpenRequestReader, OpenRequestDeniedReader, OpenResponseReader,
                                      ProxyReader, TokenReader, ListProxyReader, HidenRequestReader, HidenRequestDeniedReader, HidenResponseReader
{}
