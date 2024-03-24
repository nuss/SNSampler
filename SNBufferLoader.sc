SNBufferLoader {
	classvar <all;
	var <name, <buffers, <server;

	*initClass {
		all = ();
	}

	*new { |name, buffers, server|
		^super.newCopyArgs(
			name,
			buffers !? { buffers.asList },
			server ?? { Server.default }
		).init;
	}

	init {
		all.put(name.asSymbol, this);
		buffers ?? {
			buffers = List[];
		}
	}

	load {
		Dialog.openPanel({ |paths|
			server.waitForBoot {
				paths.do { |path|
					buffers.add(Buffer.read(server, path, action: { |buf|
						[buf.bufnum, buf.path].postln
					}))
				}
			}
		}, multipleSelection: true)
	}

}