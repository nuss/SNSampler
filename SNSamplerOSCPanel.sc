SNSamplerOSCPanel {
	classvar <all;
	var <sampler, <>oscAddr, <>oscCmdPrefix, <>backupBuffersPrefix;
	var widgets;

	*initClass {
		all = ();
	}

	*new { |sampler, oscAddr, oscCmdPrefix, backupBuffersPrefix|
		if (sampler.isNil or: { sampler.class != SNSampler }) {
			Error("A new SNSamplerOSCPanel needs an existing SNSampler instance!").throw;
		} {
			^super.newCopyArgs(sampler, oscAddr, oscCmdPrefix, backupBuffersPrefix).init;
		}
	}

	init {
		sampler.controllerKeys = sampler.controllerKeys.add(\osc);
		widgets = (
			ins: "%-inBusses",
			buffers: "%-activateBuffers",
			resetBufs: "%-resetBuffers",
			resetAll: "%-resetAll",
			startStop: "%-start/Stop",
		);
		CVCenter.use(widgets.ins.format(sampler.name), \audioin!sampler.numBuffers, 0, sampler.name);
		CVCenter.use(widgets.buffers.format(sampler.name), \false!sampler.numBuffers, tab: sampler.name);
		CVCenter.use(widgets.resetBufs.format(sampler.name), \false!sampler.numBuffers, tab: sampler.name);
		CVCenter.use(widgets.resetAll.format(sampler.name), \false, tab: sampler.name);
		CVCenter.use(widgets.startStop.format(sampler.name), \false, tab: sampler.name);
	}
}