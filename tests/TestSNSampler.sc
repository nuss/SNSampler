TestSNSampler : UnitTest {
	var sampler1, sampler2;

	setUp {
		var server1 = Server.named.at(\test1) ?? { Server(\test1, NetAddr("127.0.0.1", 57111)) },
		server2 = Server.named.at(\test2) ?? { Server(\test2, NetAddr("127.0.0.1", 57112)) };
		// "currentMethod setup: %\n".postf(currentMethod);
		sampler1 = SNSampler(tempo: 2, beatsPerBar: 7, numBars: 2, numBuffers: 7, server: server1);
		sampler2 = SNSampler(tempo: 2, beatsPerBar: 7, numBars: 2, numBuffers: 7, server: server2);
		// sampler1.server.dumpOSC(3);
		// sampler2.server.dumpOSC(3);
	}

	tearDown {
		switch (currentMethod.class.asSymbol,
			'TestSNSSampler:test_init', { sampler1.clear };
			'TestSNSSampler:test_start', {
				sampler2.clear;
				CVCenter.removeAll;
				if (CVCenter.window.notNil and: { CVCenter.window.isClosed.not }) {
					CVCenter.window.close;
				};
				Window.allWindows.select{ |w| w.name == "sampler in" }.do(_.close);
			}
		)
	}

	test_init {
		this.assertEquals(sampler1.server.addr.ip, "127.0.0.1", "the sampler's server IP address should be '127.0.0.1'");
		this.assertEquals(sampler1.server.addr.port, 57111, "the sampler's server port should be 57111");
		this.assertEquals(sampler1.clock.tempo, 2, "the clock's tempo should be 2");
		this.assertEquals(sampler1.clock.seconds, sampler1.clock.beats / 2 - sampler1.server.latency, "the clock's seconds should be the clock's beats  divided by 2 minus the server's latency");
		this.assertEquals(sampler1.numBuffers, 7, "sampler1.numBuffers should equal 7");
		this.assertEquals(sampler1.beatsPerBar, 7, "beatsPerBar should equal 7");
	}

	test_start {
		this.bootServer(sampler2.server);
		sampler2.start;
		this.wait({ sampler2.buffersAllocated }, "buffers should have been allocated within 1 second", 1);
		"sampler2.isPlaying: %, server is running: %, sampler2.buffers: %\n".postf(sampler2.isPlaying, sampler2.server.serverRunning, sampler2.buffers);
		this.assertEquals(sampler2.buffers[0].numFrames, sampler2.server.sampleRate * 7 * 2, "the frames of an allocated buffer should equal sampler2.sampleRate * 7 * 2");
		this.assertEquals(sampler2.buffers.collect(_.bufnum), [0, 1, 2, 3, 4, 5, 6], "the bufnums of the allocated buffers should equal [0, 1, 2, 3, 4, 5, 6]");
		this.wait({ sampler2.isPlaying }, "sampler should be playing within 2 seconds", 10);
		sampler2.server.quit;
	}
}